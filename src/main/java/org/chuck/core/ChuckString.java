package org.chuck.core;

/**
 * Represents a string object in ChucK.
 */
public class ChuckString extends ChuckObject {
    private final String value;

    public ChuckString(String value) {
        super(ChuckType.STRING);
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
