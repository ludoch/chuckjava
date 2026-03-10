package org.chuck.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an array in ChucK.
 * ChucK arrays can be both indexed and associative.
 */
public class ChuckArray extends ChuckObject {
    private final long[] intData;
    private final double[] floatData;
    private final Object[] objectData;
    
    // For associative behavior
    private final Map<String, Long> assocInt = new HashMap<>();
    private final Map<String, Double> assocFloat = new HashMap<>();
    private final Map<String, Object> assocObject = new HashMap<>();

    public ChuckArray(ChuckType type, int size) {
        super(type);
        this.intData = new long[size];
        this.floatData = new double[size];
        this.objectData = new Object[size];
    }

    public void setInt(int index, long value) {
        intData[index] = value;
    }

    public long getInt(int index) {
        return intData[index];
    }

    public void setFloat(int index, double value) {
        floatData[index] = value;
    }

    public double getFloat(int index) {
        return floatData[index];
    }

    public void setObject(int index, Object value) {
        objectData[index] = value;
    }

    public Object getObject(int index) {
        return objectData[index];
    }

    public int size() {
        return intData.length;
    }

    // Associative access
    public void setAssocInt(String key, long value) { assocInt.put(key, value); }
    public long getAssocInt(String key) { return assocInt.getOrDefault(key, 0L); }
}
