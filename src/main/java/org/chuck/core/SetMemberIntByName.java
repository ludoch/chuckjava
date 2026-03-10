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
    private static final Map<String, Integer> MEMBER_OFFSETS = Map.of(
        "freq",    0,
        "phase",   1,
        "gain",    1,
        "width",   1,
        "pan",     0,
        "mix",     0,
        "delay",   0,
        "feedback",1
    );

    private final String memberName;

    public SetMemberIntByName(String memberName) {
        this.memberName = memberName;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        ChuckObject obj = (ChuckObject) shred.reg.popObject();
        long rawValue = shred.reg.popLong();

        // User-defined class field assignment
        if (obj instanceof UserObject uo) {
            uo.setPrimitiveField(memberName, rawValue);
            shred.reg.pushObject(uo);
            return;
        }

        // Decode: small absolute values are plain integers; otherwise double bits.
        double doubleVal = (Math.abs(rawValue) < 2_000_000L)
                ? (double) rawValue
                : Double.longBitsToDouble(rawValue);

        // Encode for setData: always store as double bits so subclasses can read via getDataAsDouble
        long encodedForSetData = Double.doubleToRawLongBits(doubleVal);

        // Build conventional setter name: "freq" -> "setFreq"
        String setter = "set"
                + Character.toUpperCase(memberName.charAt(0))
                + memberName.substring(1);

        boolean called = tryInvoke(obj, setter, doubleVal, rawValue);

        // Fallback: call setData(index, encoded) so ChuckObject mocks work too
        if (!called) {
            Integer idx = MEMBER_OFFSETS.get(memberName);
            if (idx != null) {
                obj.setData(idx, encodedForSetData);
            }
        }

        // Push the object back so the chuck expression can be chained
        shred.reg.pushObject(obj);
    }

    /** Returns true if a typed setter was found and invoked. */
    private boolean tryInvoke(ChuckObject obj, String setter, double doubleVal, long rawValue) {
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
                    m.invoke(obj, rawValue); return true;
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
