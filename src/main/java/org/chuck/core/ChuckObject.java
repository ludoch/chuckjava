package org.chuck.core;

/**
 * Base class for all objects in the ChucK Virtual Machine.
 */
public class ChuckObject {
    private final ChuckType type;
    
    // Member data for user-defined classes
    private final long[] data;
    private final Object[] refs;

    public ChuckObject(ChuckType type) {
        this.type = type;
        this.data = new long[type.getDataSize()];
        this.refs = new Object[type.getRefSize()];
    }

    public ChuckType getType() {
        return type;
    }

    public void setData(int index, long value) {
        if (index < data.length) {
            data[index] = value;
        }
    }

    public long getData(int index) {
        return (index < data.length) ? data[index] : 0;
    }

    /**
     * Gets data at index and treats it as a double.
     */
    protected double getDataAsDouble(int index) {
        long raw = getData(index);
        if (raw > -1000000 && raw < 1000000) return (double) raw;
        return Double.longBitsToDouble(raw);
    }

    public void setRef(int index, Object obj) {
        if (index < refs.length) {
            refs[index] = obj;
        }
    }

    public Object getRef(int index) {
        return (index < refs.length) ? refs[index] : null;
    }
}
