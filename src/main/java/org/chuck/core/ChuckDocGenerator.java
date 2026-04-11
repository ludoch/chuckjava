package org.chuck.core;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Generates Markdown documentation for all built-in UGens using introspection. */
public class ChuckDocGenerator {

  public static void generate(String outputPath) throws IOException {
    Set<String> names = UGenRegistry.getRegisteredNames();
    List<String> sortedNames = new ArrayList<>(names);
    Collections.sort(sortedNames);

    try (PrintWriter out = new PrintWriter(new FileWriter(outputPath))) {
      out.println("# ChucK-Java UGen Reference");
      out.println();
      out.println("This document lists all built-in Unit Generators supported by ChucK-Java.");
      out.println();

      for (String name : sortedNames) {
        out.println("## " + name);

        Class<?> clazz = findClass(name);
        if (clazz != null) {
          // Check for class-level doc annotation
          doc classDoc = clazz.getAnnotation(doc.class);
          if (classDoc != null) {
            out.println("Description: " + classDoc.value());
            out.println();
          }

          out.println("Class: `" + clazz.getName() + "`");
          out.println();

          // Fields (parameters)
          List<Field> fields = new ArrayList<>();
          for (Field f : clazz.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && !f.getName().equals("sampleRate")) {
              fields.add(f);
            }
          }

          if (!fields.isEmpty()) {
            out.println("### Parameters");
            for (Field f : fields) {
              out.println("- `" + f.getName() + "` (" + f.getType().getSimpleName() + ")");
            }
            out.println();
          }

          // Methods
          List<Method> methods = new ArrayList<>();
          for (Method m : clazz.getMethods()) {
            if (m.getDeclaringClass() == clazz
                || m.getDeclaringClass().getSimpleName().contains("Osc")
                || m.getDeclaringClass().getSimpleName().contains("Filter")) {
              if (!Modifier.isStatic(m.getModifiers())
                  && !m.getName().equals("tick")
                  && !m.getName().equals("compute")) {
                methods.add(m);
              }
            }
          }

          if (!methods.isEmpty()) {
            out.println("### Methods");
            for (Method m : methods) {
              doc methodDoc = m.getAnnotation(doc.class);
              out.print("- `" + m.getName() + "(");
              Class<?>[] params = m.getParameterTypes();
              for (int i = 0; i < params.length; i++) {
                out.print(params[i].getSimpleName() + (i < params.length - 1 ? ", " : ""));
              }
              out.print(")` -> " + m.getReturnType().getSimpleName());
              if (methodDoc != null) {
                out.print(" : " + methodDoc.value());
              }
              out.println();
            }
            out.println();
          }
        } else {
          out.println("Category: Built-in Unit Generator");
          out.println();
        }
      }
    }
  }

  private static Class<?> findClass(String typeName) {
    String[] packages = {
      "org.chuck.audio.osc",
      "org.chuck.audio.filter",
      "org.chuck.audio.fx",
      "org.chuck.audio.stk",
      "org.chuck.audio.analysis",
      "org.chuck.audio.util",
      "org.chuck.core"
    };

    // Special mapping for common case mismatches
    String realName = typeName;
    if (typeName.equals("ADSR")) realName = "Adsr";
    if (typeName.equals("LPF")) realName = "Lpf";

    for (String pkg : packages) {
      try {
        return Class.forName(pkg + "." + realName);
      } catch (ClassNotFoundException ignored) {
      }
    }
    return null;
  }

  public static void main(String[] args) throws IOException {
    generate("UGEN_REFERENCE.md");
    System.out.println("Generated UGEN_REFERENCE.md with rich metadata.");
  }
}
