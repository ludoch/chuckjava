package org.chuck.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Sets a named member on a ChucK object using reflection.
 * Stack layout at execution: [value ... object] (object on top).
 */
public class SetMemberIntByName implements ChuckInstr {
    /** Standard member-name → data-index mapping (matches Osc/ChuckUGen conventions). */
    public static final Map<String, Integer> MEMBER_OFFSETS = Map.of(
        "freq",     0,
        "gain",     1,
        "width",    2,
        "phase",    3,
        "pan",      4,
        "mix",      5,
        "delay",    6,
        "feedback", 7
    );

    private final String memberName;

    public SetMemberIntByName(String memberName) {
        this.memberName = memberName;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        // Stack layout: [value, object] (object on top)
        if (shred.reg.getSp() < 2) {
            shred.reg.pop(shred.reg.getSp());
            return;
        }
        Object rawObj = shred.reg.popObject();
        if (rawObj == null) {
            shred.reg.pop();
            shred.reg.push(0L);
            return;
        }

        // Now pop the value (LHS of the chuck operator)
        Object valObj = null;
        double doubleVal = 0;
        boolean isObjVal = shred.reg.isObject(0);
        
        if (isObjVal) {
            valObj = shred.reg.popObject();
        } else {
            doubleVal = shred.reg.popAsDouble();
        }

        // Build conventional setter name: "freq" -> "setFreq"
        String setter = "set"
                + Character.toUpperCase(memberName.charAt(0))
                + memberName.substring(1);


        boolean called = false;
        if (isObjVal) {
            called = tryInvokeObj(rawObj, setter, valObj);
            if (!called) called = tryInvokeObj(rawObj, memberName, valObj);
        } else {
            // Special fields for complex/polar/vec
            if (rawObj instanceof ChuckArray arr && arr.vecTag != null) {
                if (arr.vecTag.equals("complex")) {
                    if (memberName.equals("re")) { arr.setFloat(0, doubleVal); called = true; }
                    else if (memberName.equals("im")) { arr.setFloat(1, doubleVal); called = true; }
                } else if (arr.vecTag.equals("polar")) {
                    if (memberName.equals("mag")) { arr.setFloat(0, doubleVal); called = true; }
                    else if (memberName.equals("phase")) { arr.setFloat(1, doubleVal); called = true; }
                } else if (arr.vecTag.startsWith("vec")) {
                    switch (memberName) {
                        case "x" -> {
                            arr.setFloat(0, doubleVal);
                            called = true;
                        }
                        case "y" -> {
                            arr.setFloat(1, doubleVal);
                            called = true;
                        }
                        case "z" -> {
                            arr.setFloat(2, doubleVal);
                            called = true;
                        }
                        case "w" -> {
                            arr.setFloat(3, doubleVal);
                            called = true;
                        }
                        default -> {
                        }
                    }
                }
            }
            if (!called) called = tryInvoke(rawObj, setter, doubleVal);
            if (!called) called = tryInvoke(rawObj, memberName, doubleVal);
        }
        
        if (!called) {
        }

        // Handle UserObject (user-defined ChucK classes) with named fields
        if (!called && rawObj instanceof UserObject uo) {
            if (isObjVal) uo.setObjectField(memberName, valObj instanceof ChuckObject co ? co : null);
            else if (uo.isFloatField(memberName)) uo.setFloatField(memberName, doubleVal);
            else uo.setPrimitiveField(memberName, (long) doubleVal);
            called = true;
        }

        // Fallback: call setData so ChuckObject mocks work too
        if (!called && !isObjVal && rawObj instanceof ChuckObject obj) {
            Integer idx = MEMBER_OFFSETS.get(memberName);
            if (idx != null) {
                obj.setData(idx, doubleVal);
                called = true;
            }
        }
        // Final fallback: try direct public field reflection
        if (!called && !isObjVal) {
            try {
                java.lang.reflect.Field f = rawObj.getClass().getField(memberName);
                Class<?> ft = f.getType();
                if (ft == int.class) f.setInt(rawObj, (int) doubleVal);
                else if (ft == long.class) f.setLong(rawObj, (long) doubleVal);
                else if (ft == float.class) f.setFloat(rawObj, (float) doubleVal);
                else if (ft == double.class) f.setDouble(rawObj, doubleVal);
            } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException ignored) {}
        }

        // Push the value back so the chuck expression can be chained
        if (isObjVal) shred.reg.pushObject(valObj);
        else if (rawObj instanceof UserObject uo2 && !uo2.isFloatField(memberName)) shred.reg.push((long) doubleVal);
        else shred.reg.push(doubleVal);
    }

    private boolean tryInvokeObj(Object obj, String setter, Object val) {
        for (Method m : obj.getClass().getMethods()) {
            if (!m.getName().equals(setter)) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length != 1) continue;
            
            Object finalVal = val;
            if (val instanceof ChuckString && params[0] == String.class) {
                finalVal = val.toString();
            } else if (val instanceof ChuckDuration cd && (params[0] == double.class || params[0] == Double.class)) {
                finalVal = cd.samples();
            } else if (val instanceof ChuckDuration cd && (params[0] == float.class || params[0] == Float.class)) {
                finalVal = (float) cd.samples();
            } else if (val instanceof ChuckDuration cd && (params[0] == int.class || params[0] == Integer.class)) {
                finalVal = (int) cd.samples();
            } else if (val instanceof ChuckDuration cd && (params[0] == long.class || params[0] == Long.class)) {
                finalVal = (long) cd.samples();
            } else if (val instanceof Number && (params[0] == float.class || params[0] == Float.class)) {
                finalVal = ((Number) val).floatValue();
            } else if (val != null && !params[0].isAssignableFrom(val.getClass())) {
                continue;
            }
            if (val == null && params[0].isPrimitive()) continue;
            
            try {
                m.invoke(obj, finalVal); return true;
            } catch (IllegalAccessException | InvocationTargetException ignored) {}
        }
        return false;
    }

    private boolean tryInvoke(Object obj, String setter, double doubleVal) {
        for (Method m : obj.getClass().getMethods()) {
            if (!m.getName().equals(setter)) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length != 1) continue;

            try {
                Class<?> p = params[0];
                if (p == double.class || p == Double.class) {
                    m.invoke(obj, doubleVal); return true;
                } else if (p == float.class || p == Float.class) {
                    m.invoke(obj, (float) doubleVal); return true;
                } else if (p == int.class || p == Integer.class) {
                    m.invoke(obj, (int) doubleVal); return true;
                } else if (p == long.class || p == Long.class) {
                    m.invoke(obj, (long) doubleVal); return true;
                } else if (p == boolean.class || p == Boolean.class) {
                    m.invoke(obj, doubleVal != 0.0); return true;
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {}
        }
        return false;
    }
}
