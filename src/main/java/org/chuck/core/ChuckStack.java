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
        System.err.println("  PUSH OBJ: " + obj + " at " + sp);
        objects[sp] = obj;
        isObject[sp] = true;
        isDouble[sp] = false;
        sp++;
    }
public long popLong() {
    if (sp <= 0) return 0L;
    sp--;
    long raw = primitives[sp];
    long val = isDouble[sp] ? (long)Double.longBitsToDouble(raw) : raw;
    System.err.println("  POP LONG: " + val + " from " + sp);
    objects[sp] = null;
    primitives[sp] = 0;
    isDouble[sp] = false;
    isObject[sp] = false;
    return val;
}

public double popDouble() {
    if (sp <= 0) return 0.0;
    sp--;
    long raw = primitives[sp];
    double val = isDouble[sp] ? Double.longBitsToDouble(raw) : (double) raw;
    System.err.println("  POP DOUBLE: " + val + " from " + sp);
    objects[sp] = null;
    primitives[sp] = 0;
    isDouble[sp] = false;
    isObject[sp] = false;
    return val;
}

public Object popObject() {
    if (sp <= 0) return null;
    sp--;
    Object o = objects[sp];
    System.err.println("  POP OBJ: " + o + " from " + sp);
    objects[sp] = null;
    primitives[sp] = 0;
    isDouble[sp] = false;
    isObject[sp] = false;
    return o;
}

    public Object pop() {
        if (sp <= 0) return null;
        if (isObject[sp - 1]) return popObject();
        if (isDouble[sp - 1]) return popDouble();
        return popLong();
    }

    public void pop(int n) {
        for (int i = 0; i < n; i++) pop();
    }

    public double popAsDouble() {
        if (sp <= 0) return 0.0;
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
        if (sp <= 0) return 0L;
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

    public void dup() {
        if (sp <= 0) throw new RuntimeException("ChucK stack underflow on Dup");
        int src = sp - 1;
        if (isObject[src]) pushObject(objects[src]);
        else if (isDouble[src]) push(Double.longBitsToDouble(primitives[src]));
        else push(primitives[src]);
    }

    public void swap() {
        if (sp < 2) throw new RuntimeException("ChucK stack underflow on Swap");
        int i1 = sp - 1, i2 = sp - 2;
        
        long tmpP = primitives[i1];
        boolean tmpD = isDouble[i1];
        boolean tmpO = isObject[i1];
        Object tmpObj = objects[i1];
        
        primitives[i1] = primitives[i2];
        isDouble[i1] = isDouble[i2];
        isObject[i1] = isObject[i2];
        objects[i1] = objects[i2];
        
        primitives[i2] = tmpP;
        isDouble[i2] = tmpD;
        isObject[i2] = tmpO;
        objects[i2] = tmpObj;
    }

    public int getSp() { return sp; }
    public void setSp(int newSp) {
        sp = newSp;
    }

    public boolean isObject(int depth) { 
        int idx = sp - 1 - depth;
        if (idx < 0 || idx >= sp) return false;
        return isObject[idx]; 
    }
    public boolean isDouble(int depth) { 
        int idx = sp - 1 - depth;
        if (idx < 0 || idx >= primitives.length) return false;
        return isDouble[idx]; 
    }
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
