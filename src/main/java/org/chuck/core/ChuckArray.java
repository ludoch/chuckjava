package org.chuck.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an array in ChucK.
 * ChucK arrays can be both indexed and associative.
 * They are dynamic and can grow via the append (<<) operator.
 */
public class ChuckArray extends ChuckObject {
    private final List<Long> intData = new ArrayList<>();
    private final List<Double> floatData = new ArrayList<>();
    private final List<Object> objectData = new ArrayList<>();
    private final List<Byte> types = new ArrayList<>(); // 0=int, 1=float, 2=object
    
    // For associative behavior
    private final Map<String, Long> assocInt = new HashMap<>();
    @SuppressWarnings("unused")
    private final Map<String, Double> assocFloat = new HashMap<>();
    @SuppressWarnings("unused")
    private final Map<String, Object> assocObject = new HashMap<>();

    public ChuckArray(ChuckType type, int size) {
        super(type);
        for (int i = 0; i < size; i++) {
            intData.add(0L);
            floatData.add(0.0);
            objectData.add(null);
            types.add((byte)1); // default to float/double for vec types
        }
    }

    public void setInt(int index, long value) {
        ensureCapacity(index);
        intData.set(index, value);
        types.set(index, (byte)0);
    }

    public long getInt(int index) {
        if (index < 0) index = intData.size() + index;
        if (index < 0 || index >= intData.size()) return 0;
        return intData.get(index);
    }

    public void setFloat(int index, double value) {
        ensureCapacity(index);
        floatData.set(index, value);
        types.set(index, (byte)1);
    }

    public double getFloat(int index) {
        if (index < 0) index = floatData.size() + index;
        if (index < 0 || index >= floatData.size()) return 0.0;
        return floatData.get(index);
    }

    public boolean isDoubleAt(int index) {
        if (index < 0) index = types.size() + index;
        if (index < 0 || index >= types.size()) return false;
        return types.get(index) == 1;
    }

    public boolean isObjectAt(int index) {
        if (index < 0) index = types.size() + index;
        if (index < 0 || index >= types.size()) return false;
        return types.get(index) == 2;
    }

    public void setObject(int index, Object value) {
        ensureCapacity(index);
        objectData.set(index, value);
        types.set(index, (byte)2);
    }

    public Object getObject(int index) {
        if (index < 0) index = objectData.size() + index;
        if (index < 0 || index >= objectData.size()) return null;
        return objectData.get(index);
    }

    public int size() {
        return types.size();
    }

    public void popOut(int index) {
        if (index < 0 || index >= types.size()) return;
        intData.remove(index);
        floatData.remove(index);
        objectData.remove(index);
        types.remove(index);
    }

    public void erase(int index) {
        popOut(index);
    }

    public void clear() {
        intData.clear();
        floatData.clear();
        objectData.clear();
        types.clear();
    }

    // append (<<) support
    public ChuckArray append(long val) {
        int idx = types.size();
        ensureCapacity(idx);
        intData.set(idx, val);
        types.set(idx, (byte)0);
        return this;
    }

    public ChuckArray append(double val) {
        int idx = types.size();
        ensureCapacity(idx);
        floatData.set(idx, val);
        types.set(idx, (byte)1);
        return this;
    }

    public ChuckArray append(Object val) {
        int idx = types.size();
        ensureCapacity(idx);
        objectData.set(idx, val);
        types.set(idx, (byte)2);
        return this;
    }

    private void ensureCapacity(int index) {
        while (types.size() <= index) {
            intData.add(0L);
            floatData.add(0.0);
            objectData.add(null);
            types.add((byte)0);
        }
    }

    // Associative access
    public void setAssocInt(String key, long value) { assocInt.put(key, value); }
    public long getAssocInt(String key) { return assocInt.getOrDefault(key, 0L); }

    /** Remove first element. */
    public void popFront() {
        if (types.isEmpty()) return;
        intData.remove(0);
        floatData.remove(0);
        objectData.remove(0);
        types.remove(0);
    }

    /** Remove elements from index `from` (inclusive) to `to` (exclusive). */
    public void erase(int from, int to) {
        from = Math.max(0, from);
        to = Math.min(types.size(), to);
        for (int i = to - 1; i >= from; i--) {
            intData.remove(i);
            floatData.remove(i);
            objectData.remove(i);
            types.remove(i);
        }
    }

    /** Shuffle elements in place (Fisher-Yates). */
    public void shuffle() {
        java.util.Random rng = new java.util.Random();
        for (int i = types.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            // Swap all parallel arrays
            Long ti = intData.get(i), tj = intData.get(j);
            intData.set(i, tj); intData.set(j, ti);
            Double fi = floatData.get(i), fj = floatData.get(j);
            floatData.set(i, fj); floatData.set(j, fi);
            Object oi = objectData.get(i), oj = objectData.get(j);
            objectData.set(i, oj); objectData.set(j, oi);
            Byte byi = types.get(i), byj = types.get(j);
            types.set(i, byj); types.set(j, byi);
        }
    }

    /** Shuffle using a seeded Random (for Math.srandom determinism). */
    public void shuffle(java.util.Random rng) {
        for (int i = types.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            Long ti = intData.get(i), tj = intData.get(j);
            intData.set(i, tj); intData.set(j, ti);
            Double fi = floatData.get(i), fj = floatData.get(j);
            floatData.set(i, fj); floatData.set(j, fi);
            Object oi = objectData.get(i), oj = objectData.get(j);
            objectData.set(i, oj); objectData.set(j, oi);
            Byte byi = types.get(i), byj = types.get(j);
            types.set(i, byj); types.set(j, byi);
        }
    }

    /** Get a double value regardless of how it was stored. */
    public double getNumeric(int index) {
        if (index < 0) index = types.size() + index;
        if (index < 0 || index >= types.size()) return 0.0;
        if (types.get(index) == 1) return floatData.get(index);
        return (double) intData.get(index);
    }
}
