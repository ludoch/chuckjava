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

    public int getSp() {
        return sp;
    }
}
