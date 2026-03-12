package org.chuck.core;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Sets a named member on a ChucK object using reflection.
 * Stack layout at execution: [value ... object] (object on top).
 * Pops the object and value, calls the appropriate setter, then pushes the object back
 * (ChucK chuck-operator convention: the RHS is returned).
 *
 * Falls back to ChuckObject.setData(index, rawValue) if no typed setter is found,
 * so plain ChuckObject mocks/subclasses that only override setData still work.
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
        ChuckObject obj = (ChuckObject) shred.reg.popObject();
        double doubleVal = shred.reg.popAsDouble();

        if (obj == null) {
            shred.reg.push(doubleVal);   // restore balance; push value back as no-op result
            return;
        }

        // Build conventional setter name: "freq" -> "setFreq"
        String setter = "set"
                + Character.toUpperCase(memberName.charAt(0))
                + memberName.substring(1);

        boolean called = tryInvoke(obj, setter, doubleVal);
        if (!called) {
            // Also try the raw name: "keyOn" -> "keyOn"
            called = tryInvoke(obj, memberName, doubleVal);
        }

        // Fallback: call setData so ChuckObject mocks work too
        if (!called) {
            Integer idx = MEMBER_OFFSETS.get(memberName);
            if (idx != null) {
                obj.setData(idx, doubleVal);
            }
        }

        // Push the object back so the chuck expression can be chained
        shred.reg.pushObject(obj);
    }

    /** Returns true if a typed setter was found and invoked. */
    private boolean tryInvoke(ChuckObject obj, String setter, double doubleVal) {
        if (obj == null) return false;
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
            } catch (Exception ignored) {
                // Fall through and try next overload
            }
        }
        return false;
    }
}
