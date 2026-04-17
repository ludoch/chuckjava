package org.chuck;

import java.nio.file.*;
import org.chuck.core.ChuckVM;

/** Helper to run a single .ck file in a fresh JVM. */
public class SingleTestRunner {
  public static void main(String[] args) {
    if (args.length < 1) {
      System.exit(2);
    }
    String path = args[0];
    int sampleRate = 44100;
    int timeoutSeconds = 5;

    ChuckVM vm = new ChuckVM(sampleRate);
    try {
      String source = Files.readString(Paths.get(path));
      vm.run(source, path);

      int steps = 50; // 5 seconds in 100ms steps
      int samplesPerStep = sampleRate / 10;
      boolean finished = false;

      long startTime = System.currentTimeMillis();
      for (int i = 0; i < steps; i++) {
        if (vm.getActiveShredCount() == 0) {
          finished = true;
          break;
        }
        vm.advanceTime(samplesPerStep);
        if (System.currentTimeMillis() - startTime > timeoutSeconds * 1000) break;
      }
      
      if (!finished) {
        System.err.println("TIMEOUT");
        System.exit(3);
      }
      System.out.println("SUCCESS");
      System.exit(0);
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(1);
    } finally {
      vm.shutdown();
    }
  }
}
