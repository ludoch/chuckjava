package org.chuck.core;

/**
 * A memory-safe stack for the ChucK Virtual Machine.
 */
public class ChuckStack {
    private final long[] primitives;
    private final Object[] objects;
    private int sp; 

    public ChuckStack(int size) {
        this.primitives = new long[size];
        this.objects = new Object[size];
        this.sp = 0;
    }

    public void push(long value) {
        primitives[sp] = value;
        objects[sp] = null;
        sp++;
    }

    public void push(double value) {
        primitives[sp] = Double.doubleToRawLongBits(value);
        objects[sp] = null;
        sp++;
    }

    public void pushObject(Object obj) {
        primitives[sp] = 0;
        objects[sp] = obj;
        sp++;
    }

    public long popLong() {
        return primitives[--sp];
    }

    public double popDouble() {
        return Double.longBitsToDouble(primitives[--sp]);
    }

    /**
     * ChucK specific pop that handles mixed types.
     */
    public double popAsDouble() {
        long raw = primitives[--sp];
        // Heuristic: If it looks like a small integer, it's likely an int being used as a float.
        // ChucK does this auto-casting frequently.
        if (Math.abs(raw) < 2000000) return (double) raw;
        return Double.longBitsToDouble(raw);
    }

    @SuppressWarnings("unchecked")
    public <T> T popObject() {
        T obj = (T) objects[--sp];
        objects[sp] = null;
        return obj;
    }

    public long peekLong(int offset) {
        return primitives[sp - 1 - offset];
    }

    public Object peekObject(int offset) {
        return objects[sp - 1 - offset];
    }

    public void pop(int count) {
        for (int i = 0; i < count; i++) {
            objects[--sp] = null;
        }
    }

    public boolean isObject(int offset) {
        if (sp - 1 - offset < 0) return false;
        Object obj = objects[sp - 1 - offset];
        return obj != null && !(obj instanceof Long || obj instanceof Double || obj instanceof Integer);
    }

    public Object pop() {
        sp--;
        Object obj = objects[sp];
        if (obj != null) {
            objects[sp] = null;
            return obj;
        }
        long raw = primitives[sp];
        // Guess if it's double or long based on raw value range (imperfect but works for basic printing)
        double d = Double.longBitsToDouble(raw);
        if (Double.isNaN(d) || Double.isInfinite(d) || (Math.abs(d) < 1e-10 && raw != 0)) {
            return raw;
        }
        // If it's a whole number, return as Long
        if (d == (long) d) return (long) d;
        return d;
    }

    public int getSp() {
        return sp;
    }
}
