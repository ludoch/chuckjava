package org.chuck.audio.chugins;

import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.chuck.core.UGenRegistry;

/**
 * Dynamic Chugin (.chug) and native shared library loader using Java 27 Foreign Function & Memory
 * API (Project Panama). Probes search directories and registers discovered plugins directly into
 * the central UGenRegistry without JNI.
 */
public class ChuginLoader {
  private static final List<SymbolLookup> LOADED_LIBRARIES = new ArrayList<>();

  /**
   * Scans given search path strings for .chug, .so, .dylib, or .dll plugin files and loads them.
   * Also verifies that all standard Chugin simulation classes are registered.
   *
   * @param searchPaths list of directory strings to scan
   * @return total number of newly registered or loaded chugins
   */
  public static synchronized int loadChuginsFromPaths(List<String> searchPaths) {
    int loadedCount = 0;

    // First ensure all built-in simulation chugins are registered in UGenRegistry
    registerBuiltinChugins();

    if (searchPaths == null || searchPaths.isEmpty()) {
      return loadedCount;
    }

    for (String pathStr : searchPaths) {
      File dir = new File(pathStr);
      if (!dir.exists() || !dir.isDirectory()) continue;

      File[] files =
          dir.listFiles(
              (d, name) -> {
                String n = name.toLowerCase();
                return n.endsWith(".chug")
                    || n.endsWith(".so")
                    || n.endsWith(".dylib")
                    || n.endsWith(".dll");
              });
      if (files == null) continue;

      for (File pluginFile : files) {
        if (probeAndRegisterLibrary(pluginFile)) {
          loadedCount++;
        }
      }
    }
    return loadedCount;
  }

  private static boolean probeAndRegisterLibrary(File pluginFile) {
    try {
      // Use shared arena so native library remains loaded across the VM lifecycle
      SymbolLookup lookup =
          SymbolLookup.libraryLookup(pluginFile.getAbsolutePath(), Arena.ofShared());
      LOADED_LIBRARIES.add(lookup);

      String baseName = pluginFile.getName();
      int dotIdx = baseName.lastIndexOf('.');
      if (dotIdx > 0) baseName = baseName.substring(0, dotIdx);

      // Clean up lib prefix on POSIX if present
      if (baseName.startsWith("lib") && baseName.length() > 3) {
        baseName = baseName.substring(3);
      }

      // First check for standard chugin query/compute symbol
      Optional<MemorySegment> computeSym = lookup.find(baseName + "_compute");
      if (computeSym.isEmpty()) {
        computeSym = lookup.find("chugin_compute");
      }
      if (computeSym.isEmpty()) {
        computeSym = lookup.find("process_sample");
      }

      MemorySegment sym = computeSym.orElse(MemorySegment.NULL);
      final String ugenName = baseName;

      UGenRegistry.register(ugenName, (sr, args) -> new NativeUGenBridge(ugenName, sym, sr));
      System.out.println(
          "[ChuginLoader] ✅ Loaded native chugin: " + ugenName + " from " + pluginFile.getName());
      return true;
    } catch (Exception e) {
      System.out.println(
          "[ChuginLoader] ⚠️ Skipping library "
              + pluginFile.getName()
              + " (not a compatible FFM chugin: "
              + e.getMessage()
              + ")");
      return false;
    }
  }

  private static void registerBuiltinChugins() {
    UGenRegistry.register("Bitcrusher", (sr, args) -> new Bitcrusher());
    UGenRegistry.register("FoldbackSaturator", (sr, args) -> new FoldbackSaturator());
    UGenRegistry.register("KasFilter", (sr, args) -> new KasFilter(sr));
    UGenRegistry.register("WPDiodeLadder", (sr, args) -> new WPDiodeLadder(sr));
    UGenRegistry.register("WPKorg35", (sr, args) -> new WPKorg35(sr));
    UGenRegistry.register("ExpDelay", (sr, args) -> new ExpDelay(sr));
    UGenRegistry.register("Perlin", (sr, args) -> new Perlin(sr));
    UGenRegistry.register("MagicSine", (sr, args) -> new MagicSine(sr));
    UGenRegistry.register("FIR", (sr, args) -> new FIR(sr));
    UGenRegistry.register("Overdrive", (sr, args) -> new Overdrive());
    UGenRegistry.register("PowerADSR", (sr, args) -> new PowerADSR());
    UGenRegistry.register("ExpEnv", (sr, args) -> new ExpEnv());
    UGenRegistry.register("WinFuncEnv", (sr, args) -> new WinFuncEnv());
  }
}
