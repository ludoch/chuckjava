package org.chuck.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines a ChucK type.
 */
public class ChuckType {
    private final String name;
    private final ChuckType parent;
    private final int dataSize; // Size in longs
    private final int refSize;  // Number of object references

    public ChuckType(String name, ChuckType parent, int dataSize, int refSize) {
        this.name = name;
        this.parent = parent;
        this.dataSize = dataSize;
        this.refSize = refSize;
    }

    public String getName() {
        return name;
    }

    public ChuckType getParent() {
        return parent;
    }

    public int getDataSize() {
        return dataSize;
    }

    public int getRefSize() {
        return refSize;
    }

    public boolean isSubtypeOf(ChuckType other) {
        ChuckType current = this;
        while (current != null) {
            if (current == other) return true;
            current = current.parent;
        }
        return false;
    }

    // Built-in base types
    public static final ChuckType OBJECT = new ChuckType("Object", null, 0, 0);
    public static final ChuckType EVENT = new ChuckType("Event", OBJECT, 0, 0);
    public static final ChuckType INT = new ChuckType("int", null, 0, 0);
    public static final ChuckType FLOAT = new ChuckType("float", null, 0, 0);
    public static final ChuckType DUR = new ChuckType("dur", null, 0, 0);
    public static final ChuckType TIME = new ChuckType("time", null, 0, 0);
    public static final ChuckType STRING = new ChuckType("string", OBJECT, 0, 0);
    public static final ChuckType ARRAY = new ChuckType("array", OBJECT, 0, 0);
}
