package org.chuck.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ChuckConfig {
  private static final List<String> searchPaths = new ArrayList<>();
  private static String currentScriptDir = null;

  static {
    // Default search paths
    searchPaths.add(".");
    searchPaths.add("..");
    searchPaths.add("examples/data");
    searchPaths.add("chuck-samples/src/main/resources");
    searchPaths.add("../chuck-samples/src/main/resources");
    searchPaths.add("chuck-samples/src/main/resources/examples/data");
    searchPaths.add("../chuck-samples/src/main/resources/examples/data");
    searchPaths.add("chuck-samples/src/main/resources/examples/book/digital-artists/audio");
    searchPaths.add("../chuck-samples/src/main/resources/examples/book/digital-artists/audio");
  }

  public static void setCurrentScriptDir(String dir) {
    currentScriptDir = dir;
  }

  public static void addSearchPath(String path) {
    if (!searchPaths.contains(path)) {
      searchPaths.add(0, path); // Add to front for priority
    }
  }

  public static File resolveFile(String filename) {
    if (filename == null || filename.isEmpty()) return new File("");

    File f = new File(filename);
    if (f.isAbsolute() && f.exists()) {
      return f;
    }

    // 1. Try relative to current script
    if (currentScriptDir != null) {
      File resolved = new File(currentScriptDir, filename);
      if (resolved.exists()) return resolved;
    }

    // 2. Try relative to CWD
    if (f.exists()) return f;

    // 3. Try search paths
    for (String path : searchPaths) {
      File resolved = new File(path, filename);
      if (resolved.exists()) {
        return resolved;
      }
    }

    // 4. Try normalized path (fix backslashes on Linux/Mac etc)
    String normalized = filename.replace("\\", "/");
    if (!normalized.equals(filename)) {
      return resolveFile(normalized);
    }

    return f; // Return original if not found
  }
}
