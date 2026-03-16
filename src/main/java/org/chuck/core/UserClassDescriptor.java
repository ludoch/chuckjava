package org.chuck.core;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

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
    Map<String, Object> staticObjects
) {
    public UserClassDescriptor(String name, String parentName, List<String[]> fields, Map<String, ChuckCode> methods, Map<String, ChuckCode> staticMethods) {
        this(name, parentName, fields, methods, staticMethods, 
             new java.util.concurrent.ConcurrentHashMap<>(), 
             new java.util.concurrent.ConcurrentHashMap<>(), 
             new java.util.concurrent.ConcurrentHashMap<>());
    }
}
