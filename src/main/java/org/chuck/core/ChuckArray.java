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
        if (index < 0 || index >= intData.size()) return 0;
        return intData.get(index);
    }

    public void setFloat(int index, double value) {
        ensureCapacity(index);
        floatData.set(index, value);
        types.set(index, (byte)1);
    }

    public double getFloat(int index) {
        if (index < 0 || index >= floatData.size()) return 0.0;
        return floatData.get(index);
    }

    public boolean isDoubleAt(int index) {
        if (index < 0 || index >= types.size()) return false;
        return types.get(index) == 1;
    }

    public boolean isObjectAt(int index) {
        if (index < 0 || index >= types.size()) return false;
        return types.get(index) == 2;
    }

    public void setObject(int index, Object value) {
        ensureCapacity(index);
        objectData.set(index, value);
        types.set(index, (byte)2);
    }

    public Object getObject(int index) {
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
}
