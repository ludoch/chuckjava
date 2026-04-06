package org.chuck.core;

/**
 * ChuckTypeObj — runtime Type object exposed to ChucK programs.
 * Returned by typeof(expr) when used as an object (vs string).
 * Wraps a type name and optional parent name for basic introspection.
 *
 * ChucK API:  Type t; typeof(x) => t;  t.name() => string;  t.parent() => Type;
 */
public class ChuckTypeObj extends ChuckObject {

    private final String typeName;
    private final String parentName;

    public ChuckTypeObj(String typeName, String parentName) {
        super(ChuckType.OBJECT);
        this.typeName   = typeName;
        this.parentName = parentName != null ? parentName : "";
    }

    public ChuckTypeObj(String typeName) { this(typeName, null); }

    /** Returns the type name as a string. */
    public ChuckString name() { return new ChuckString(typeName); }

    /** Returns the parent type name as a string. */
    public ChuckString parent() { return new ChuckString(parentName); }

    /** Returns 1 if the type extends (or is) the given type name. */
    public long isa(String name) { return typeName.equals(name) || parentName.equals(name) ? 1L : 0L; }

    /** Returns 1 if this type is the same as the other. */
    public long eq(ChuckTypeObj other) { return other != null && typeName.equals(other.typeName) ? 1L : 0L; }

    @Override
    public String toString() { return typeName; }
}
