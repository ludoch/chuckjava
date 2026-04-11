package org.chuck.core;

import java.util.List;
import java.util.Map;
import org.chuck.compiler.ChuckAST.AccessModifier;

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
    boolean isInterface,
    ChuckCode staticInitCode,
    Map<String, AccessModifier> fieldAccess,
    Map<String, AccessModifier> methodAccess,
    AccessModifier classAccess,
    String doc,
    Map<String, String> methodDocs,
    Map<String, String> fieldDocs
) {
    public UserClassDescriptor(String name, String parentName, List<String[]> fields, List<String[]> staticFields, Map<String, ChuckCode> methods, Map<String, ChuckCode> staticMethods) {
        this(name, parentName, fields, staticFields, methods, staticMethods,
             new java.util.concurrent.ConcurrentHashMap<>(),
             new java.util.concurrent.ConcurrentHashMap<>(),
             new java.util.concurrent.ConcurrentHashMap<>(),
             null, false, false, null,
             new java.util.HashMap<>(), new java.util.HashMap<>(), AccessModifier.PUBLIC, null, new java.util.HashMap<>(), new java.util.HashMap<>());
    }
    public UserClassDescriptor(String name, String parentName, List<String[]> fields, List<String[]> staticFields, Map<String, ChuckCode> methods, Map<String, ChuckCode> staticMethods,
                               Map<String, Long> staticInts, Map<String, Boolean> staticIsDouble, Map<String, Object> staticObjects) {
        this(name, parentName, fields, staticFields, methods, staticMethods, staticInts, staticIsDouble, staticObjects, null, false, false, null,
             new java.util.HashMap<>(), new java.util.HashMap<>(), AccessModifier.PUBLIC, null, new java.util.HashMap<>(), new java.util.HashMap<>());
    }
    public UserClassDescriptor(String name, String parentName, List<String[]> fields, List<String[]> staticFields, Map<String, ChuckCode> methods, Map<String, ChuckCode> staticMethods,
                               Map<String, Long> staticInts, Map<String, Boolean> staticIsDouble, Map<String, Object> staticObjects,
                               ChuckCode preCtorCode) {
        this(name, parentName, fields, staticFields, methods, staticMethods, staticInts, staticIsDouble, staticObjects, preCtorCode, false, false, null,
             new java.util.HashMap<>(), new java.util.HashMap<>(), AccessModifier.PUBLIC, null, new java.util.HashMap<>(), new java.util.HashMap<>());
    }
    public UserClassDescriptor(String name, String parentName, List<String[]> fields, List<String[]> staticFields, Map<String, ChuckCode> methods, Map<String, ChuckCode> staticMethods,
                               Map<String, Long> staticInts, Map<String, Boolean> staticIsDouble, Map<String, Object> staticObjects,
                               ChuckCode preCtorCode, boolean isAbstract, boolean isInterface) {
        this(name, parentName, fields, staticFields, methods, staticMethods, staticInts, staticIsDouble, staticObjects, preCtorCode, isAbstract, isInterface, null,
             new java.util.HashMap<>(), new java.util.HashMap<>(), AccessModifier.PUBLIC, null, new java.util.HashMap<>(), new java.util.HashMap<>());
    }
    public UserClassDescriptor(String name, String parentName, List<String[]> fields, List<String[]> staticFields, Map<String, ChuckCode> methods, Map<String, ChuckCode> staticMethods,
                               Map<String, Long> staticInts, Map<String, Boolean> staticIsDouble, Map<String, Object> staticObjects,
                               ChuckCode preCtorCode, boolean isAbstract, boolean isInterface, ChuckCode staticInitCode) {
        this(name, parentName, fields, staticFields, methods, staticMethods, staticInts, staticIsDouble, staticObjects, preCtorCode, isAbstract, isInterface, staticInitCode,
             new java.util.HashMap<>(), new java.util.HashMap<>(), AccessModifier.PUBLIC, null, new java.util.HashMap<>(), new java.util.HashMap<>());
    }
}
