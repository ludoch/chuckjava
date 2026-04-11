package org.chuck.core;

import java.util.Set;

/**
 * Centralized metadata and constants for the ChucK language implementation.
 */
public class ChuckLanguage {

    public static final Set<String> PRIMITIVE_TYPES = Set.of(
            "int", "float", "string", "time", "dur", "void", "complex", "polar"
    );

    public static final Set<String> VECTOR_TYPES = Set.of(
            "vec2", "vec3", "vec4"
    );

    public static final Set<String> CORE_DATA_TYPES = Set.of(
            "int", "float", "string", "time", "dur", "void", "vec2", "vec3", "vec4", 
            "complex", "polar", "Object", "Array", "Type", "Function", "auto",
            "MidiMsg", "HidMsg", "OscMsg", "FileIO", "IO", "SerialIO", 
            "OscIn", "OscOut", "OscBundle", "MidiIn", "MidiOut", "Hid", 
            "StringTokenizer", "RegEx", "Reflect"
    );

    public static final Set<String> CORE_UGENS = Set.of(
            "OscIn", "OscOut", "OscMsg", "FileIO", "IO", "Std", "Math", "Machine", 
            "UGen", "UGen_Multi", "UGen_Stereo", "UAna", "Shred", "Thread", "ChucK", "Event"
    );

    public static boolean isVectorType(String type) {
        return type != null && VECTOR_TYPES.contains(type);
    }

    public static boolean isPrimitiveType(String type) {
        return type != null && (PRIMITIVE_TYPES.contains(type) || VECTOR_TYPES.contains(type));
    }

    public static boolean isObjectType(String type) {
        if (type == null) return false;
        if (type.endsWith("[]")) return true;
        if (CORE_UGENS.contains(type)) return true;
        Set<String> objectTypes = Set.of(
            "complex", "polar", "vec2", "vec3", "vec4", "string", "Object", "Event", "Type", "Function"
        );
        return objectTypes.contains(type) || (!PRIMITIVE_TYPES.contains(type) && !VECTOR_TYPES.contains(type));
    }
}
