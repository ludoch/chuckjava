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
    Map<String, ChuckCode> methods,
    Map<String, ChuckCode> staticMethods,
    Map<String, Long> staticInts,
    Map<String, Boolean> staticIsDouble,
    Map<String, Object> staticObjects,
    ChuckCode preCtorCode,
    boolean isAbstract,
    boolean isInterface
) {
    public UserClassDescriptor(String name, String parentName, List<String[]> fields, Map<String, ChuckCode> methods, Map<String, ChuckCode> staticMethods) {
        this(name, parentName, fields, methods, staticMethods,
             new java.util.concurrent.ConcurrentHashMap<>(),
             new java.util.concurrent.ConcurrentHashMap<>(),
             new java.util.concurrent.ConcurrentHashMap<>(),
             null, false, false);
    }
    public UserClassDescriptor(String name, String parentName, List<String[]> fields, Map<String, ChuckCode> methods, Map<String, ChuckCode> staticMethods,
                               Map<String, Long> staticInts, Map<String, Boolean> staticIsDouble, Map<String, Object> staticObjects) {
        this(name, parentName, fields, methods, staticMethods, staticInts, staticIsDouble, staticObjects, null, false, false);
    }
    public UserClassDescriptor(String name, String parentName, List<String[]> fields, Map<String, ChuckCode> methods, Map<String, ChuckCode> staticMethods,
                               Map<String, Long> staticInts, Map<String, Boolean> staticIsDouble, Map<String, Object> staticObjects,
                               ChuckCode preCtorCode) {
        this(name, parentName, fields, methods, staticMethods, staticInts, staticIsDouble, staticObjects, preCtorCode, false, false);
    }
}
