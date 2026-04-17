package org.chuck.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ChuckConfig {
  private static final List<String> searchPaths = new ArrayList<>();

  static {
    // Default search paths
    searchPaths.add(".");
    searchPaths.add("examples/data");
    searchPaths.add("examples/book/digital-artists/chapter4/audio");
    searchPaths.add("examples/book/digital-artists/chapter5/audio");
    searchPaths.add("examples/book/digital-artists/chapter9/audio");
  }

  public static void addSearchPath(String path) {
    if (!searchPaths.contains(path)) {
      searchPaths.add(0, path); // Add to front for priority
    }
  }

  public static File resolveFile(String filename) {
    File f = new File(filename);
    if (f.exists()) return f;

    for (String path : searchPaths) {
      File resolved = new File(path, filename);
      if (resolved.exists()) return resolved;
    }
    return f; // Return original if not found
  }
}
