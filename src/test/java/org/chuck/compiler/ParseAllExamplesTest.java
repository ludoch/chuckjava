package org.chuck.compiler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.antlr.v4.runtime.*;
import org.junit.jupiter.api.Test;

/**
 * Attempts to parse every .ck file in the repository using ANTLR. Verifies that the parser can
 * handle all examples and tests.
 */
public class ParseAllExamplesTest {

  @Test
  public void parseAllCkFiles() throws IOException {
    List<Path> roots = Arrays.asList(Paths.get("examples"), Paths.get("src/test"));
    List<Path> ckFiles = new ArrayList<>();

    for (Path root : roots) {
      if (Files.exists(root)) {
        try (Stream<Path> walk = Files.walk(root)) {
          ckFiles.addAll(
              walk.filter(p -> p.toString().endsWith(".ck")).collect(Collectors.toList()));
        }
      }
    }

    if (ckFiles.isEmpty()) {
      System.out.println("No .ck files found – skipping.");
      return;
    }

    Collections.sort(ckFiles);

    List<String> failed = new ArrayList<>();
    Map<String, List<String>> errorGroups = new TreeMap<>();
    int total = ckFiles.size();
    int passed = 0;
    int expectedFailures = 0;

    System.out.println("🔍 Starting ANTLR4 Parse Test on " + total + " files...");

    for (Path file : ckFiles) {
      String pathStr = file.toString().replace('\\', '/');
      // Some files might be intentionally invalid syntax for testing error reporting
      boolean isSyntaxErrorTest =
          pathStr.contains("error-syntax")
              || pathStr.contains("error-incomplete")
              || pathStr.contains("error-auto-void")
              || pathStr.contains("error-tabs.ck")
              || pathStr.contains("05-Global/01b.ck"); // Legacy 'external' keyword test

      try {
        String source = Files.readString(file);
        CharStream input = CharStreams.fromString(source);

        ChuckANTLRLexer lexer = new ChuckANTLRLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(
            new BaseErrorListener() {
              @Override
              public void syntaxError(
                  Recognizer<?, ?> recognizer,
                  Object offendingSymbol,
                  int line,
                  int charPositionInLine,
                  String msg,
                  RecognitionException e) {
                throw new RuntimeException(
                    "Lexer error at " + line + ":" + charPositionInLine + " - " + msg);
              }
            });

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ChuckANTLRParser parser = new ChuckANTLRParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(
            new BaseErrorListener() {
              @Override
              public void syntaxError(
                  Recognizer<?, ?> recognizer,
                  Object offendingSymbol,
                  int line,
                  int charPositionInLine,
                  String msg,
                  RecognitionException e) {
                throw new RuntimeException(
                    "Parser error at " + line + ":" + charPositionInLine + " - " + msg);
              }
            });

        parser.program();

        if (isSyntaxErrorTest) {
          failed.add(pathStr + " → Expected syntax error but passed");
        } else {
          passed++;
        }
      } catch (Exception e) {
        if (isSyntaxErrorTest) {
          passed++;
          expectedFailures++;
        } else {
          String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
          // Normalise message for grouping
          String key =
              msg.replaceAll("line \\d+", "line N")
                  .replaceAll("column \\d+", "col N")
                  .replaceAll("'[^']{1,40}'", "'X'");
          errorGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(pathStr);
          failed.add(pathStr + " → " + msg);
        }
      }
    }

    // Print summary
    System.out.printf(
        "%n=== Parse Results (ANTLR): %d / %d passed (incl. %d expected failures) ===%n%n",
        passed, total, expectedFailures);

    if (!errorGroups.isEmpty()) {
      System.out.println("--- Error groups (sorted by frequency) ---");
      errorGroups.entrySet().stream()
          .sorted((a, b) -> b.getValue().size() - a.getValue().size())
          .forEach(
              e -> {
                System.out.printf("[%d files] %s%n", e.getValue().size(), e.getKey());
                e.getValue().stream().limit(5).forEach(f -> System.out.println("    " + f));
                if (e.getValue().size() > 5)
                  System.out.printf("    ... and %d more%n", e.getValue().size() - 5);
              });
    }

    if (!failed.isEmpty()) {
      System.out.println("\nDetailed Failures:");
      failed.forEach(f -> System.out.println("  " + f));
    }

    assertTrue(
        failed.isEmpty(), failed.size() + " files failed to parse (see output above for details)");
  }
}
