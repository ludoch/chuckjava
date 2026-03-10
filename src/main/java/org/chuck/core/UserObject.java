package org.chuck.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime instance of a user-defined ChucK class.
 * Holds primitive fields (int/float stored as raw long bits) and object fields,
 * plus a reference to the compiled method bytecodes shared across all instances.
 */
public class UserObject extends ChuckObject {
    public final String className;
    /** Raw-long storage for int/float fields. */
    private final Map<String, Long> primitiveFields = new LinkedHashMap<>();
    /** Object-typed fields. */
    private final Map<String, ChuckObject> objectFields = new LinkedHashMap<>();
    /** Compiled method bodies, shared across all instances of this class. */
    public final Map<String, ChuckCode> methods;

    /**
     * @param fieldDefs list of [type, name] pairs from the class declaration
     */
    public UserObject(String className, List<String[]> fieldDefs, Map<String, ChuckCode> methods) {
        super(new ChuckType(className, ChuckType.OBJECT, 0, 0));
        this.className = className;
        this.methods = methods;
        for (String[] f : fieldDefs) {
            primitiveFields.put(f[1], 0L);
        }
    }

    public boolean hasPrimitiveField(String name) { return primitiveFields.containsKey(name); }
    public boolean hasObjectField(String name)    { return objectFields.containsKey(name); }

    public long         getPrimitiveField(String name)        { return primitiveFields.getOrDefault(name, 0L); }
    public void         setPrimitiveField(String name, long v){ primitiveFields.put(name, v); }

    public ChuckObject  getObjectField(String name)           { return objectFields.get(name); }
    public void         setObjectField(String name, ChuckObject o) { objectFields.put(name, o); }
}
