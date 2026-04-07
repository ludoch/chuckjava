package org.chuck.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ChuckTypeObj — runtime Type object exposed to ChucK programs.
 * Returned by typeof(expr) when used as an object (vs string).
 * Wraps a type name and optional parent name for basic introspection.
 *
 * ChucK API:  Type t; typeof(x) => t;  t.name() => string;  t.parent() => Type;  t.methods() => Function[];
 */
public class ChuckTypeObj extends ChuckObject {

    private final String typeName;
    private final String parentName;
    private final ChuckVM vm;

    public ChuckTypeObj(String typeName, String parentName, ChuckVM vm) {
        super(ChuckType.OBJECT);
        this.typeName   = typeName;
        this.parentName = parentName != null ? parentName : "";
        this.vm = vm;
    }

    public ChuckTypeObj(String typeName, String parentName) {
        this(typeName, parentName, null);
    }

    public ChuckTypeObj(String typeName) { this(typeName, null, null); }

    /** Returns the type name as a string. */
    public ChuckString name() { return new ChuckString(typeName); }

    /** Returns the parent type. */
    public ChuckTypeObj parent() {
        if (parentName.isEmpty() || parentName.equals("Object")) return null;
        return new ChuckTypeObj(parentName, "Object", vm);
    }

    /** Returns 1 if the type extends (or is) the given type name. */
    public long isa(String name) {
        if (typeName.equals(name)) return 1L;
        if (parentName.equals(name)) return 1L;
        if (vm != null) {
            UserClassDescriptor desc = vm.getUserClass(typeName);
            while (desc != null) {
                if (desc.name().equals(name)) return 1L;
                desc = desc.parentName() != null ? vm.getUserClass(desc.parentName()) : null;
            }
        }
        return 0L;
    }

    /** Returns 1 if this type is the same as the other. */
    public long eq(ChuckTypeObj other) { return other != null && typeName.equals(other.typeName) ? 1L : 0L; }

    /** Returns an array of all methods defined in this type. */
    public ChuckArray methods() {
        ChuckArray arr = new ChuckArray(ChuckType.ARRAY, 0);
        if (vm == null) return arr;

        UserClassDescriptor desc = vm.getUserClass(typeName);
        if (desc != null) {
            // User-defined class
            for (ChuckCode code : desc.methods().values()) {
                arr.appendObject(new ChuckFunction(code));
            }
            for (ChuckCode code : desc.staticMethods().values()) {
                arr.appendObject(new ChuckFunction(code));
            }
        } else {
            // Built-in type (Reflection)
            try {
                // Try to find a matching class in known packages
                Class<?> clazz = null;
                try { clazz = Class.forName("org.chuck.audio." + typeName); } catch (ClassNotFoundException e1) {
                    try { clazz = Class.forName("org.chuck.core." + typeName); } catch (ClassNotFoundException e2) {}
                }

                if (clazz != null) {
                    for (java.lang.reflect.Method m : clazz.getMethods()) {
                        // Filter out Object methods and internal stuff
                        if (m.getDeclaringClass() == Object.class) continue;
                        arr.appendObject(new ChuckFunction(m.getName(), m.getParameterCount(), m.getReturnType().getSimpleName()));
                    }
                }
            } catch (Exception ignored) {}
        }
        return arr;
    }

    @Override
    public String toString() { return typeName; }
}
