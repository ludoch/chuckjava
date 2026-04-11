package org.chuck.core;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Generates Markdown documentation for all built-in UGens. */
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

        // For now, we don't have easy access to the source code here,
        // but we can instantiate one to check its type or just list it.
        // In a more advanced version, we would parse the @doc annotations
        // from the .java files using reflection or the emitter's info.

        out.println("Category: Built-in Unit Generator");
        out.println();
      }
    }
  }

  public static void main(String[] args) throws IOException {
    generate("UGEN_REFERENCE.md");
    System.out.println("Generated UGEN_REFERENCE.md");
  }
}
