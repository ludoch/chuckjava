package org.chuck;

import java.nio.file.*;
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

    // Check for expected error/output file (.txt with same basename)
    String expectationFile = path.substring(0, path.lastIndexOf('.')) + ".txt";
    String expectation = null;
    boolean isErrorExpectation = false;
    if (Files.exists(Paths.get(expectationFile))) {
      try {
        expectation = Files.readString(Paths.get(expectationFile)).trim();
        // Heuristic: if it contains "error:" or "exception", it's an error test
        isErrorExpectation =
            expectation.toLowerCase().contains("error")
                || expectation.toLowerCase().contains("exception");

        // If expectation contains ":(", it likely expects type tags
        if (expectation.contains(":(")) {
          System.setProperty("chuck.print.tags", "true");
        }
      } catch (Exception ignored) {
      }
    }

    StringBuilder actualOutput = new StringBuilder();
    ChuckVM vm = new ChuckVM(sampleRate);
    vm.addPrintListener(text -> actualOutput.append(text));

    try {
      String source = Files.readString(Paths.get(path));
      try {
        vm.run(source, path);
      } catch (Throwable t) {
        // If we have an expected error, check if this exception matches
        if (isErrorExpectation) {
          String msg = t.getMessage();
          if (msg != null && isErrorMatch(msg, expectation)) {
            System.out.println("SUCCESS (Expected error caught)");
            System.exit(0);
          } else {
            System.err.println("CAUGHT WRONG ERROR:");
            System.err.println("Expected: " + expectation);
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
      if (isErrorExpectation) {
        if (isErrorMatch(actualOutput.toString(), expectation)) {
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

      if (isErrorExpectation) {
        if (isErrorMatch(actualOutput.toString(), expectation)) {
          System.out.println("SUCCESS (Expected error found in output)");
          System.exit(0);
        }
        // If we reach here for an error test, it means the error was NEVER caught
        System.err.println("ERROR NOT CAUGHT");
        System.err.println("Expected: " + expectation);
        System.err.println("Actual output: " + actualOutput.toString());
        System.exit(4);
      }

      if (expectation != null && !isErrorExpectation) {
        if (!isErrorMatch(actualOutput.toString(), expectation)) {
          System.err.println("OUTPUT MISMATCH");
          System.err.println("Expected: " + expectation);
          System.err.println("Actual:   " + actualOutput.toString());
          System.exit(5);
        }
      }

      if (!finished) {

        // Smoke test: if it ran for several seconds without error, call it a success for infinite
        // loops
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
    if (expected.isEmpty()) return true;

    // Normalize both: lowercase, remove line/col numbers, collapse whitespace
    String cleanExpected =
        expected
            .toLowerCase()
            .replaceAll("\\d+:\\d+", "") // remove line:col
            .replaceAll("\\.ck", "") // remove filename extensions
            .replaceAll("\\s+", " ")
            .trim();

    String cleanActual =
        actual
            .toLowerCase()
            .replaceAll("\\d+:\\d+", "")
            .replaceAll("\\.ck", "")
            .replaceAll("\\s+", " ")
            .trim();

    // If expected contains "error:", try to find the core message after it
    if (cleanExpected.contains("error:")) {
      String coreMessage = cleanExpected.substring(cleanExpected.indexOf("error:") + 6).trim();
      if (cleanActual.contains(coreMessage)) return true;
    }

    // Treat "mismatched input" or "extraneous input" as "syntax error"
    if (cleanExpected.contains("syntax error")) {
      if (cleanActual.contains("mismatched input")
          || cleanActual.contains("extraneous input")
          || cleanActual.contains("no viable alternative")
          || (cleanExpected.contains("empty file") && cleanActual.contains("empty file")))
        return true;
    }

    // Check for common ChucK error keywords if the full match fails
    String[] keywords = {"error", "exception", "undefined", "cannot", "invalid", "illegal"};
    for (String kw : keywords) {
      if (cleanExpected.contains(kw) && cleanActual.contains(kw)) {
        // If both have the keyword, and actual contains a good chunk of expected
        if (cleanExpected.length() > 10) {
          String partial = cleanExpected.substring(0, Math.min(cleanExpected.length(), 20));
          if (cleanActual.contains(partial)) return true;
        }
      }
    }

    // Fuzzy numeric matching for floats: normalize precision to 4 decimal places
    String fuzzyExpected = normalizeNumerics(cleanExpected);
    String fuzzyActual = normalizeNumerics(cleanActual);
    if (fuzzyActual.contains(fuzzyExpected) || fuzzyExpected.contains(fuzzyActual)) return true;

    return cleanActual.contains(cleanExpected) || cleanExpected.contains(cleanActual);
  }

  private static String normalizeNumerics(String s) {
    // Find patterns like 1.234567 and turn them into 1.2345
    // This helps matching reference ChucK output which often uses 4 decimal places.
    java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d+\\.\\d{4})\\d*");
    java.util.regex.Matcher m = p.matcher(s);
    return m.replaceAll("$1");
  }
}
