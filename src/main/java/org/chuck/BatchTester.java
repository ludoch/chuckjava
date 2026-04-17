package org.chuck;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BatchTester {

  private static final int TIMEOUT_SECONDS = 7; // Slightly longer for process overhead

  public static void main(String[] args) {
    List<Path> allFiles = new ArrayList<>();
    try {
      allFiles.addAll(findCkFiles(Paths.get("src/test")));
      allFiles.addAll(findCkFiles(Paths.get("examples")));
    } catch (IOException e) {
      System.err.println("Error finding .ck files: " + e.getMessage());
      return;
    }

    System.out.println("🔍 Found " + allFiles.size() + " files to test.");

    int total = allFiles.size();
    AtomicInteger passed = new AtomicInteger(0);
    AtomicInteger failed = new AtomicInteger(0);
    AtomicInteger timedOut = new AtomicInteger(0);
    AtomicInteger processed = new AtomicInteger(0);
    List<String> failures = Collections.synchronizedList(new ArrayList<>());

    String classpath = System.getProperty("java.class.path");
    
    // Increased parallelism since memory is isolated per process
    ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    Thread progressThread = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          Thread.sleep(5000);
          System.out.printf("--- Heartbeat Progress: %d/%d (P:%d, F:%d, T:%d) ---\n",
              processed.get(), total, passed.get(), failed.get(), timedOut.get());
        } catch (InterruptedException e) {
          break;
        }
      }
    });
    progressThread.setDaemon(true);
    progressThread.start();

    for (Path file : allFiles) {
      executor.submit(() -> {
        String path = file.toString();
        try {
          ProcessBuilder pb = new ProcessBuilder(
              "java",
              "--enable-preview",
              "--add-modules", "jdk.incubator.vector",
              "-cp", classpath,
              "org.chuck.SingleTestRunner",
              path
          );
          
          Process p = pb.start();
          if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            timedOut.incrementAndGet();
            failures.add(path + " (Process Timed out)");
          } else {
            int exitCode = p.exitValue();
            if (exitCode == 0) {
              passed.incrementAndGet();
            } else if (exitCode == 3) {
              timedOut.incrementAndGet();
              failures.add(path + " (VM Timed out)");
            } else {
              failed.incrementAndGet();
              // Capture error output
              try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                  String errorLine = reader.lines().collect(Collectors.joining("\n"));
                  failures.add(path + " (Exit code " + exitCode + "):\n" + errorLine);
              }
            }
          }
        } catch (Exception e) {
          failed.incrementAndGet();
          failures.add(path + " (Launcher error: " + e.getMessage() + ")");
        } finally {
          processed.incrementAndGet();
        }
      });
    }

    executor.shutdown();
    try {
      executor.awaitTermination(60, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      System.err.println("Batch test interrupted.");
    }
    progressThread.interrupt();

    System.out.println("\n--- 📊 Batch Test Report ---");
    System.out.println("Total:     " + total);
    System.out.println("Passed:    " + passed.get());
    System.out.println("Failed:    " + failed.get());
    System.out.println("Timed Out: " + timedOut.get());

    if (!failures.isEmpty()) {
      // Save report to file instead of dumping to stdout if too large
      try (PrintWriter pw = new PrintWriter(new FileWriter("test-report.txt"))) {
          pw.println("--- 📊 Batch Test Report ---");
          pw.println("Total:     " + total);
          pw.println("Passed:    " + passed.get());
          pw.println("Failed:    " + failed.get());
          pw.println("Timed Out: " + timedOut.get());
          pw.println("\n--- 📝 Failure Details ---");
          failures.forEach(f -> {
            pw.println("--------------------------------------------------");
            pw.println(f);
          });
          System.out.println("✅ Detailed report saved to test-report.txt");
      } catch (IOException e) {
          System.err.println("Failed to write report: " + e.getMessage());
      }
    }

    if (failed.get() > 0 || timedOut.get() > 0) {
      System.exit(1);
    }
  }

  private static List<Path> findCkFiles(Path root) throws IOException {
    if (!Files.exists(root)) return Collections.emptyList();
    try (Stream<Path> stream = Files.walk(root)) {
      return stream
          .filter(p -> p.toString().endsWith(".ck"))
          .filter(p -> !p.toString().contains(".disabled"))
          .filter(p -> !p.toString().contains("06-Errors"))
          .filter(p -> !p.toString().contains("examples/hid"))
          .filter(p -> !p.toString().contains("examples/midi"))
          .filter(p -> !p.toString().contains("examples/osc"))
          .filter(p -> !p.toString().contains("examples/serial"))
          .filter(
              p -> {
                String path = p.toString();
                // Skip examples that are known to be interactive or infinite listeners
                if (path.contains("adc.ck")) return false;
                if (path.contains("otf_")) return false; // On-the-fly examples usually infinite
                
                for (int i = 0; i < p.getNameCount(); i++) {
                  if (p.getName(i).toString().startsWith(".")
                      && !p.getName(i).toString().endsWith(".ck")) return false;
                }
                return true;
              })
          .sorted()
          .collect(Collectors.toList());
    }
  }
}
