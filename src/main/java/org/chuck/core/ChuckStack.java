package org.chuck.core;

/**
 * A memory-safe stack for the ChucK Virtual Machine.
 */
public class ChuckStack {
    private final long[] primitives;
    private final boolean[] isDouble; // Tracks if a primitive slot contains a double bitmask
    private final Object[] objects;
    private int sp; 

    public ChuckStack(int size) {
        this.primitives = new long[size];
        this.isDouble = new boolean[size];
        this.objects = new Object[size];
        this.sp = 0;
    }

    public void push(long value) {
        primitives[sp] = value;
        isDouble[sp] = false;
        objects[sp] = null;
        sp++;
    }

    public void push(double value) {
        primitives[sp] = Double.doubleToRawLongBits(value);
        isDouble[sp] = true;
        objects[sp] = null;
        sp++;
    }

    public void pushObject(Object obj) {
        primitives[sp] = 0;
        isDouble[sp] = false;
        objects[sp] = obj;
        sp++;
    }

    public long popLong() {
        sp--;
        return primitives[sp];
    }

    public double popDouble() {
        sp--;
        return Double.longBitsToDouble(primitives[sp]);
    }

    /**
     * ChucK specific pop that handles mixed types correctly.
     */
    public double popAsDouble() {
        sp--;
        long raw = primitives[sp];
        if (isDouble[sp]) return Double.longBitsToDouble(raw);
        return (double) raw;
    }

    public boolean isDouble(int offset) {
        int idx = sp - 1 - offset;
        if (idx < 0) return false;
        return isDouble[idx];
    }

    public boolean isDoubleAt(int index) {
        if (index < 0 || index >= isDouble.length) return false;
        return isDouble[index];
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

    public long getData(int index) {
        if (index < 0 || index >= primitives.length) return 0;
        return primitives[index];
    }

    public Object getRef(int index) {
        if (index < 0 || index >= objects.length) return null;
        return objects[index];
    }

    public void setData(int index, long value) {
        if (index >= 0 && index < primitives.length) {
            primitives[index] = value;
            isDouble[index] = false;
        }
    }

    public void setData(int index, double value) {
        if (index >= 0 && index < primitives.length) {
            primitives[index] = Double.doubleToRawLongBits(value);
            isDouble[index] = true;
        }
    }

    /** Directly set the stack pointer (used by CallFunc/ReturnFunc to clean up args). */
    public void setSp(int newSp) {
        // Clear object refs above new sp to avoid memory leaks
        for (int i = newSp; i < sp; i++) objects[i] = null;
        sp = newSp;
    }
}
