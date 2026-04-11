package org.chuck.core;

import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.chuck.host.ChuckHost;

/**
 * A hot-reloading runner for Java DSL files. Watches a directory and sporks shreds in a single VM
 * instance.
 */
public class JavaMachine {
  private final ChuckHost host;
  private final Map<Path, Integer> activeShreds = new ConcurrentHashMap<>();

  public JavaMachine(ChuckHost host) {
    this.host = host;
  }

  public void watch(String dirPath) throws Exception {
    Path path = Paths.get(dirPath);
    WatchService watcher = FileSystems.getDefault().newWatchService();
    path.register(
        watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);

    System.out.println("🎸 ChucK-Java Machine Watching: " + dirPath);

    while (true) {
      WatchKey key = watcher.take();
      for (WatchEvent<?> event : key.pollEvents()) {
        Path fileName = (Path) event.context();
        if (fileName.toString().endsWith(".java")) {
          Path fullPath = path.resolve(fileName);
          reload(fullPath);
        }
      }
      key.reset();
    }
  }

  private void reload(Path path) {
    try {
      System.out.println("🔄 Reloading: " + path.getFileName());

      // 1. Remove old shred if exists
      if (activeShreds.containsKey(path)) {
        host.remove(activeShreds.get(path));
      }

      // 2. Load and spork new one
      Runnable task = ChuckDSL.load(path);
      int id = host.spork(task);
      activeShreds.put(path, id);

      System.out.println("✅ Sporked Java Shred: " + id);
    } catch (Exception e) {
      System.err.println("❌ Failed to load " + path.getFileName());
      if (host.getVM().getLogLevel() >= 2) {
        e.printStackTrace();
      }
    }
  }

  public static void main(String[] args) throws Exception {
    // Use a single shared host
    ChuckHost host = new ChuckHost(44100).withAudio(512, 2);
    JavaMachine machine = new JavaMachine(host);

    String dir = args.length > 0 ? args[0] : "examples_dsl";
    machine.watch(dir);
  }
}
