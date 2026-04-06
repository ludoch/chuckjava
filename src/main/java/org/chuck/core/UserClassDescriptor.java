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
    ChuckCode preCtorCode
) {
    public UserClassDescriptor(String name, String parentName, List<String[]> fields, List<String[]> staticFields, Map<String, ChuckCode> methods, Map<String, ChuckCode> staticMethods) {
        this(name, parentName, fields, staticFields, methods, staticMethods,
             new java.util.concurrent.ConcurrentHashMap<>(),
             new java.util.concurrent.ConcurrentHashMap<>(),
             new java.util.concurrent.ConcurrentHashMap<>(),
             null);
    }
    public UserClassDescriptor(String name, String parentName, List<String[]> fields, List<String[]> staticFields, Map<String, ChuckCode> methods, Map<String, ChuckCode> staticMethods,
                               Map<String, Long> staticInts, Map<String, Boolean> staticIsDouble, Map<String, Object> staticObjects) {
        this(name, parentName, fields, staticFields, methods, staticMethods, staticInts, staticIsDouble, staticObjects, null);
    }
}
