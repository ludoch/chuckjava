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
    /** Non-null if this class (or an ancestor) extends the built-in Event type. */
    public final ChuckEvent eventDelegate;
    /** Raw-long storage for int/float fields (floats stored as Double.doubleToRawLongBits). */
    private final Map<String, Long> primitiveFields = new LinkedHashMap<>();
    /** Names of fields declared as float/double. */
    private final java.util.Set<String> floatFields = new java.util.HashSet<>();
    /** Object-typed fields. */
    private final Map<String, ChuckObject> objectFields = new LinkedHashMap<>();
    /** Compiled method bodies, shared across all instances of this class. */
    public final Map<String, ChuckCode> methods;

    /**
     * @param fieldDefs list of [type, name] pairs from the class declaration
     */
    public UserObject(String className, List<String[]> fieldDefs, Map<String, ChuckCode> methods) {
        this(className, fieldDefs, methods, false);
    }

    public UserObject(String className, List<String[]> fieldDefs, Map<String, ChuckCode> methods, boolean extendsEvent) {
        super(new ChuckType(className, ChuckType.OBJECT, 0, 0));
        this.className = className;
        this.methods = methods;
        this.eventDelegate = extendsEvent ? new ChuckEvent() : null;
        for (String[] f : fieldDefs) {
            boolean isFloat = f.length > 0 && ("float".equals(f[0]) || "double".equals(f[0]));
            long initVal;
            if (f.length > 2 && f[2] != null) {
                try {
                    initVal = isFloat ? Double.doubleToRawLongBits(Double.parseDouble(f[2])) : Long.parseLong(f[2]);
                } catch (NumberFormatException e) {
                    initVal = isFloat ? Double.doubleToRawLongBits(0.0) : 0L;
                }
            } else {
                initVal = isFloat ? Double.doubleToRawLongBits(0.0) : 0L;
            }
            if (isFloat) floatFields.add(f[1]);
            primitiveFields.put(f[1], initVal);
        }
    }

    public boolean hasPrimitiveField(String name) { return primitiveFields.containsKey(name); }
    public boolean hasObjectField(String name)    { return objectFields.containsKey(name); }
    public boolean isFloatField(String name)      { return floatFields.contains(name); }

    public long         getPrimitiveField(String name)        { return primitiveFields.getOrDefault(name, 0L); }
    public void         setPrimitiveField(String name, long v){ primitiveFields.put(name, v); }
    /** Store a float/double field value (stores as raw double bits). */
    public void         setFloatField(String name, double v)  { primitiveFields.put(name, Double.doubleToRawLongBits(v)); }
    /** Retrieve a float/double field value (decodes raw double bits). */
    public double       getFloatField(String name)            { return Double.longBitsToDouble(primitiveFields.getOrDefault(name, 0L)); }

    public ChuckObject  getObjectField(String name)           { return objectFields.get(name); }
    public void         setObjectField(String name, ChuckObject o) { objectFields.put(name, o); }
}
