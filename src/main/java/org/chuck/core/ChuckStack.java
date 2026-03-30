package org.chuck.core;

/**
 * A memory-safe stack for the ChucK Virtual Machine.
 */
public class ChuckStack {
    private final long[] primitives;
    private final boolean[] isDouble;
    private final boolean[] isObject;
    private final Object[] objects;
    private int sp = 0;

    public ChuckStack(int size) {
        primitives = new long[size];
        isDouble = new boolean[size];
        isObject = new boolean[size];
        objects = new Object[size];
    }

    public void push(long val) {
        if (sp >= primitives.length) throw new RuntimeException("ChucK stack overflow: " + primitives.length);
        primitives[sp] = val;
        isDouble[sp] = false;
        isObject[sp] = false;
        sp++;
    }

    public void push(double val) {
        if (sp >= primitives.length) throw new RuntimeException("ChucK stack overflow: " + primitives.length);
        primitives[sp] = Double.doubleToRawLongBits(val);
        isDouble[sp] = true;
        isObject[sp] = false;
        sp++;
    }

    public void pushObject(Object obj) {
        if (sp >= primitives.length) throw new RuntimeException("ChucK stack overflow: " + primitives.length);
        objects[sp] = obj;
        isObject[sp] = true;
        isDouble[sp] = false;
        sp++;
    }

    public long popLong() {
        if (sp <= 0) throw new ArrayIndexOutOfBoundsException("ChucK stack underflow: sp=" + sp);
        sp--;
        long raw = primitives[sp];
        long val = isDouble[sp] ? Math.round(Double.longBitsToDouble(raw)) : raw;
        objects[sp] = null;
        primitives[sp] = 0;
        isDouble[sp] = false;
        isObject[sp] = false;
        return val;
    }

    public double popDouble() {
        if (sp <= 0) throw new ArrayIndexOutOfBoundsException("ChucK stack underflow: sp=" + sp);
        sp--;
        long raw = primitives[sp];
        double val = isDouble[sp] ? Double.longBitsToDouble(raw) : (double) raw;
        objects[sp] = null;
        primitives[sp] = 0;
        isDouble[sp] = false;
        isObject[sp] = false;
        return val;
    }

    public Object popObject() {
        if (sp <= 0) throw new ArrayIndexOutOfBoundsException("ChucK stack underflow: sp=" + sp);
        sp--;
        Object obj = objects[sp];
        objects[sp] = null;
        isObject[sp] = false;
        isDouble[sp] = false;
        return obj;
    }

    public Object pop() {
        if (sp <= 0) throw new ArrayIndexOutOfBoundsException("ChucK stack underflow");
        if (isObject[sp - 1]) return popObject();
        if (isDouble[sp - 1]) return popDouble();
        return popLong();
    }

    public void pop(int n) {
        for (int i = 0; i < n; i++) pop();
    }

    public double popAsDouble() {
        if (sp <= 0) throw new ArrayIndexOutOfBoundsException("ChucK stack underflow");
        int idx = sp - 1;
        if (isObject[idx]) {
            Object o = popObject();
            return switch (o) {
                case null -> 0.0;
                case ChuckDuration cd -> (double) cd.samples();
                case Number n -> n.doubleValue();
                case FileIO fio -> fio.good() ? 1.0 : 0.0;
                default -> 1.0;
            };
        }
        if (isDouble[idx]) {
            return popDouble();
        } else {
            return (double) popLong();
        }
    }

    public long popAsLong() {
        if (sp <= 0) throw new ArrayIndexOutOfBoundsException("ChucK stack underflow");
        int idx = sp - 1;
        if (isObject[idx]) {
            Object o = popObject();
            return switch (o) {
                case null -> 0L;
                case ChuckDuration cd -> (long) cd.samples();
                case Number n -> n.longValue();
                default -> 1L;
            };
        }
        if (isDouble[idx]) {
            return (long) popDouble();
        } else {
            return popLong();
        }
    }

    public int getSp() { return sp; }
    public void setSp(int newSp) {
        for (int i = newSp; i < sp; i++) {
            objects[i] = null;
            isObject[i] = false;
            isDouble[i] = false;
        }
        sp = newSp;
    }

    public boolean isObject(int depth) { return isObject[sp - 1 - depth]; }
    public boolean isDouble(int depth) { return isDouble[sp - 1 - depth]; }
    public Object peekObject(int depth) { return objects[sp - 1 - depth]; }
    public long peekLong(int depth) { return primitives[sp - 1 - depth]; }
    public double peekAsDouble(int depth) {
        int idx = sp - 1 - depth;
        if (isDouble[idx]) return Double.longBitsToDouble(primitives[idx]);
        return (double) primitives[idx];
    }

    // Random access for locals
    public long getData(int idx) { return primitives[idx]; }
    public void setData(int idx, long val) {
        primitives[idx] = val;
        isObject[idx] = false;
        isDouble[idx] = false;
    }
    public void setData(int idx, double val) {
        primitives[idx] = Double.doubleToRawLongBits(val);
        isObject[idx] = false;
        isDouble[idx] = true;
    }
    public Object getRef(int idx) { return objects[idx]; }
    public void setRef(int idx, Object obj) {
        objects[idx] = obj;
        isObject[idx] = true;
        isDouble[idx] = false;
    }
    public boolean isObjectAt(int idx) { return isObject[idx]; }
    public boolean isDoubleAt(int idx) { return isDouble[idx]; }
}
