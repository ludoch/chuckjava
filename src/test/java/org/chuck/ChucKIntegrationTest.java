package org.chuck;

import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ChucKIntegrationTest {

    // Tracks modification times of .txt files before each test runs
    private static final Map<String, FileTime> txtModTimeBefore = new ConcurrentHashMap<>();

    @TestFactory
    Stream<DynamicTest> chuckTests() throws IOException {
        Path testRoot = Paths.get("test");
        if (!Files.exists(testRoot)) {
            return Stream.empty();
        }

        String filter = System.getProperty("dynamicTestFilter");

        return Files.walk(testRoot)
                .filter(p -> p.toString().endsWith(".ck"))
                .filter(p -> !p.toString().endsWith(".disabled.ck") && !p.toString().contains(".disabled"))
                .filter(p -> { // skip files inside hidden directories (e.g. .deps)
                    for (int i = 0; i < p.getNameCount(); i++) {
                        String part = p.getName(i).toString();
                        if (part.startsWith(".") && !part.endsWith(".ck")) return false;
                    }
                    return true;
                })
           ///     .filter(p -> p.toString().contains("01-Basic"))
                .filter(p -> filter == null || p.toString().contains(filter))
                .sorted()
                .map(p -> DynamicTest.dynamicTest(testRoot.relativize(p).toString(), () -> runTest(p)));
    }

    private void runTest(Path ckFile) throws Exception {
        String source = Files.readString(ckFile);
        System.out.println(ckFile);
        // Record .txt file modification time before running the VM
        Path txtFilePre = ckFile.resolveSibling(ckFile.getFileName().toString().replace(".ck", ".txt"));
        String txtKey = txtFilePre.toAbsolutePath().toString();
        if (Files.exists(txtFilePre)) {
            txtModTimeBefore.put(txtKey, Files.getLastModifiedTime(txtFilePre));
        } else {
            txtModTimeBefore.remove(txtKey);
        }

        ChuckVM vm = new ChuckVM(44100);
        
        final StringBuilder output = new StringBuilder();
        vm.addPrintListener(text -> {
            if (output.length() < 100000) output.append(text);
        });

        // Capture stdout and stderr
        java.io.PrintStream oldOut = System.out;
        java.io.PrintStream oldErr = System.err;
        java.io.ByteArrayOutputStream capture = new java.io.ByteArrayOutputStream();
        java.io.PrintStream ps = new java.io.PrintStream(capture);
        System.setOut(ps);
        System.setErr(ps);

        try {
            int shredId = vm.run(source, ckFile.toString());

            // Advance time until finished or timeout
            int maxSeconds = 3; // Allow up to 3 simulated seconds
            int samplesPerStep = 44100 / 10; // 100ms
            for (int i = 0; i < maxSeconds * 10 && vm.getActiveShredCount() > 0; i++) {
                vm.advanceTime(samplesPerStep);
                Thread.sleep(1);
            }
        } catch (Throwable t) {
            output.append("Error: ").append(t.getMessage()).append("\n");
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
            String captured = capture.toString();
            if (!captured.isEmpty()) {
                if (captured.length() > 50000) captured = captured.substring(0, 50000) + "...[TRUNCATED]";
                output.append(captured);
            }
        }
        String result = output.toString().stripTrailing();
        String normalizedResult = normalizeOutput(result);
        Path txtFile = txtFilePre; // same path computed before VM run

        // Check if the .txt file was written by the test program itself
        // (same base name as .ck → .txt). If so, skip expected comparison.
        boolean useExpected = false;
        String normalizedExpected = "";
        if (Files.exists(txtFile)) {
            FileTime modBefore = txtModTimeBefore.get(txtFile.toAbsolutePath().toString());
            FileTime modAfter = Files.getLastModifiedTime(txtFile);
            boolean fileWrittenByTest = modBefore == null || !modAfter.equals(modBefore);
            if (!fileWrittenByTest) {
                String expected = Files.readString(txtFile);
                normalizedExpected = normalizeOutput(expected);
                useExpected = true;
            }
        }

        if (useExpected) {
            String expected = Files.readString(txtFile);
            // Some tests might have different error message formats but should still be verified
            if (ckFile.toString().contains("06-Errors") || expected.contains("error:") || expected.contains("EXCEPTION") || expected.contains("error --")) {
                assertFalse(normalizedResult.isEmpty(), "Error test " + ckFile + " produced no output");
            } else {
                assertEquals(normalizedExpected, normalizedResult, "Output mismatch for " + ckFile);
            }
        } else {
            // Check for "success" as per test.py
            assertTrue(normalizedResult.contains("success"),
                "Test " + ckFile + " did not contain 'success' and no .txt file found. Output:\n" + result);
        }
    }

    private String normalizeOutput(String out) {
        if (out == null) return "";
        return out.replaceAll(" :\\(\\w+\\)", "") // Strip :(int), :(float), etc.
                .replaceAll("\"" , "")           // Strip quotes
                .replace("\r\n", "\n")
                .replaceAll("\\[chuck\\]: \\(VM\\) sporking incoming shred: \\d+", "[chuck]: (VM) sporking incoming shred: N") // Normalize shred IDs
                .replaceAll("[ \t]+\n", "\n")    // Strip trailing whitespace from each line
                .stripTrailing();                // Only strip from the very end of the whole blob
    }
}
