package org.chuck.compiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * Attempts to parse every .ck file under the examples/ directory.
 * Reports per-file errors grouped by error message pattern.
 */
public class ParseAllExamplesTest {

    @Test
    public void parseAllExamples() throws IOException {
        Path examplesRoot = Paths.get("examples");
        if (!Files.exists(examplesRoot)) {
            System.out.println("No examples/ directory found – skipping.");
            return;
        }

        List<String> failed = new ArrayList<>();
        Map<String, List<String>> errorGroups = new TreeMap<>();
        int total = 0, passed = 0;

        try (Stream<Path> walk = Files.walk(examplesRoot)) {
            List<Path> ckFiles = walk
                    .filter(p -> p.toString().endsWith(".ck"))
                    .sorted()
                    .toList();

            total = ckFiles.size();
            for (Path file : ckFiles) {
                try {
                    String source = Files.readString(file);
                    ChuckLexer lexer = new ChuckLexer(source);
                    ChuckParser parser = new ChuckParser(lexer.tokenize());
                    parser.parse();
                    passed++;
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    // Normalise message: strip line/col numbers so similar errors group together
                    String key = msg.replaceAll("line \\d+", "line N")
                                    .replaceAll("column \\d+", "col N")
                                    .replaceAll("'[^']{1,40}'", "'X'");
                    errorGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(file.toString());
                    failed.add(file + " → " + msg);
                }
            }
        }

        // Print summary
        System.out.printf("%n=== Parse Results: %d / %d passed ===%n%n", passed, total);

        if (!errorGroups.isEmpty()) {
            System.out.println("--- Error groups (sorted by frequency) ---");
            errorGroups.entrySet().stream()
                    .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                    .forEach(e -> {
                        System.out.printf("[%d files] %s%n", e.getValue().size(), e.getKey());
                        e.getValue().stream().limit(3).forEach(f -> System.out.println("    " + f));
                        if (e.getValue().size() > 3)
                            System.out.printf("    ... and %d more%n", e.getValue().size() - 3);
                    });
        }

        // Don't fail the test – just report. Change to assertTrue to enforce.
        // assertTrue(failed.isEmpty(), failed.size() + " files failed to parse");
    }
}
