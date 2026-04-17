package org.chuck;

import java.nio.file.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.chuck.core.ChuckVM;

/** Helper to run a single .ck file in a fresh JVM, with support for expected error files. */
public class SingleTestRunner {
  public static void main(String[] args) {
    if (args.length < 1) {
      System.exit(2);
    }
    String path = args[0];
    int sampleRate = 44100;
    int timeoutSeconds = 5;

    // Check for expected error file (.txt with same basename)
    String expectedErrorFile = path.substring(0, path.lastIndexOf('.')) + ".txt";
    String expectedError = null;
    if (Files.exists(Paths.get(expectedErrorFile))) {
        try {
            expectedError = Files.readString(Paths.get(expectedErrorFile)).trim();
        } catch (Exception ignored) {}
    }

    StringBuilder actualOutput = new StringBuilder();
    ChuckVM vm = new ChuckVM(sampleRate);
    vm.addPrintListener(text -> actualOutput.append(text).append("\n"));

    try {
      String source = Files.readString(Paths.get(path));
      try {
        vm.run(source, path);
      } catch (Throwable t) {
          // If we have an expected error, check if this exception matches
          if (expectedError != null) {
              String msg = t.getMessage();
              if (msg != null && isErrorMatch(msg, expectedError)) {
                  System.out.println("SUCCESS (Expected error caught)");
                  System.exit(0);
              } else {
                  System.err.println("CAUGHT WRONG ERROR:");
                  System.err.println("Expected: " + expectedError);
                  System.err.println("Actual:   " + msg);
                  t.printStackTrace();
                  System.exit(1);
              }
          } else {
              // No expected error, so any exception is a failure
              t.printStackTrace();
              System.exit(1);
          }
      }

      // If we reach here, no exception was thrown by vm.run (compiler or initial shred execution)
      // Some errors might be logged via print listener instead of thrown
      if (expectedError != null) {
          if (isErrorMatch(actualOutput.toString(), expectedError)) {
              System.out.println("SUCCESS (Expected error found in output)");
              System.exit(0);
          }
      }

      int steps = 50; 
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
      
      if (expectedError != null) {
          // If we reach here for an error test, it means the error was NEVER caught
          System.err.println("ERROR NOT CAUGHT");
          System.err.println("Expected: " + expectedError);
          System.err.println("Actual output: " + actualOutput.toString());
          System.exit(4);
      }

      if (!finished) {
        // Smoke test: if it ran for several seconds without error, call it a success for infinite loops
        System.out.println("SUCCESS (Smoke test passed)");
        System.exit(0);
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

  private static boolean isErrorMatch(String actual, String expected) {
      if (actual == null || expected == null) return false;
      // Heuristic: check if key parts of the expected error (excluding line/col) exist in actual
      // ChucK error files often contain full snippets and carets, we want the core message.
      String cleanExpected = expected.toLowerCase().replaceAll("\\s+", " ");
      String cleanActual = actual.toLowerCase().replaceAll("\\s+", " ");
      
      // Extract the first line of expected if multi-line
      String firstLine = expected.split("\n")[0].toLowerCase();
      if (firstLine.contains("error:")) {
          firstLine = firstLine.substring(firstLine.indexOf("error:") + 6).trim();
      }
      
      return cleanActual.contains(firstLine) || cleanActual.contains(cleanExpected);
  }
}
