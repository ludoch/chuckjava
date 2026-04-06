package org.chuck.core;

import java.util.List;
import java.util.Map;

/**
 * Metadata for a user-defined ChucK class.
 */
public record UserClassDescriptor(
    String name,
    String parentName,
    List<String[]> fields,
    List<String[]> staticFields,
    Map<String, ChuckCode> methods,
    Map<String, ChuckCode> staticMethods,
    Map<String, Long> staticInts,
    Map<String, Boolean> staticIsDouble,
    Map<String, Object> staticObjects,
    ChuckCode preCtorCode,
    boolean isAbstract,
    boolean isInterface
) {
    public UserClassDescriptor(String name, String parentName, List<String[]> fields, List<String[]> staticFields, Map<String, ChuckCode> methods, Map<String, ChuckCode> staticMethods) {
        this(name, parentName, fields, staticFields, methods, staticMethods,
             new java.util.concurrent.ConcurrentHashMap<>(),
             new java.util.concurrent.ConcurrentHashMap<>(),
             new java.util.concurrent.ConcurrentHashMap<>(),
             null, false, false);
    }
    public UserClassDescriptor(String name, String parentName, List<String[]> fields, List<String[]> staticFields, Map<String, ChuckCode> methods, Map<String, ChuckCode> staticMethods,
                               Map<String, Long> staticInts, Map<String, Boolean> staticIsDouble, Map<String, Object> staticObjects) {
        this(name, parentName, fields, staticFields, methods, staticMethods, staticInts, staticIsDouble, staticObjects, null, false, false);
    }
    public UserClassDescriptor(String name, String parentName, List<String[]> fields, List<String[]> staticFields, Map<String, ChuckCode> methods, Map<String, ChuckCode> staticMethods,
                               Map<String, Long> staticInts, Map<String, Boolean> staticIsDouble, Map<String, Object> staticObjects,
                               ChuckCode preCtorCode) {
        this(name, parentName, fields, staticFields, methods, staticMethods, staticInts, staticIsDouble, staticObjects, preCtorCode, false, false);
    }
    public UserClassDescriptor(String name, String parentName, List<String[]> fields, List<String[]> staticFields, Map<String, ChuckCode> methods, Map<String, ChuckCode> staticMethods,
                               Map<String, Long> staticInts, Map<String, Boolean> staticIsDouble, Map<String, Object> staticObjects,
                               ChuckCode preCtorCode, boolean isAbstract, boolean isInterface) {
        // canonical constructor is implicitly defined, but we can explicitly define it if needed.
        // For a record, this is the canonical one.
        this.name = name;
        this.parentName = parentName;
        this.fields = fields;
        this.staticFields = staticFields;
        this.methods = methods;
        this.staticMethods = staticMethods;
        this.staticInts = staticInts;
        this.staticIsDouble = staticIsDouble;
        this.staticObjects = staticObjects;
        this.preCtorCode = preCtorCode;
        this.isAbstract = isAbstract;
        this.isInterface = isInterface;
    }
}
