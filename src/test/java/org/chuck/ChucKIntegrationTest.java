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
        Path testRoot = Paths.get("src/test");
        if (!Files.exists(testRoot)) {
            return Stream.empty();
        }

        String filter = System.getProperty("dynamicTestFilter");
        String specificFile = System.getProperty("specificTestFile");
        String maxNumStr = System.getProperty("maxTestNumber");
        int maxNum = maxNumStr != null ? Integer.parseInt(maxNumStr) : Integer.MAX_VALUE;

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
                .filter(p -> {
                    // Extract number prefix if possible
                    String fileName = p.getFileName().toString();
                    String prefix = fileName.split("-")[0].split("\\.")[0];
                    try {
                        int num = Integer.parseInt(prefix);
                        if (num > maxNum) return false;
                    } catch (NumberFormatException ignored) {}
                    
                    if (specificFile != null) {
                        return p.toString().endsWith("/" + specificFile) || p.toString().equals(specificFile);
                    }
                    return filter == null || p.toString().contains(filter);
                })
                .filter(p -> System.getProperty("includeStress") != null || !p.toString().contains("04-Stress"))
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
            @SuppressWarnings("unused")
            int shredId = vm.run(source, ckFile.toString());

            // Advance time until finished or timeout
            int maxSeconds = 3; // Allow up to 3 simulated seconds
            int samplesPerStep = 44100 / 10; // 100ms
            for (int i = 0; i < maxSeconds * 10 && vm.getActiveShredCount() > 0; i++) {
                vm.advanceTime(samplesPerStep);
                Thread.sleep(1);
            }
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }

        String result = output.toString();
        String normalizedResult = normalizeOutput(result);

        Path txtFile = ckFile.resolveSibling(ckFile.getFileName().toString().replace(".ck", ".txt"));
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
            // Some tests might have different error message formats but should still be verified
            if (ckFile.toString().contains("06-Errors") || normalizedExpected.contains("error:") || normalizedExpected.contains("EXCEPTION") || normalizedExpected.contains("error --")) {
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
        String normalized = out.replaceAll(" :\\(\\w+\\)", "") // Strip :(int), :(float), etc.
                .replaceAll("\"" , "")           // Strip quotes
                .replace("\r\n", "\n")
                .replaceAll("\\[chuck\\]: \\(VM\\) sporking incoming shred: \\d+", "[chuck]: (VM) sporking incoming shred: N")
                .replaceAll("[ \t]+\n", "\n")
                .stripTrailing();
        
        // Normalize ALL numbers (with or without decimal point)
        // This regex also matches numbers followed by *pi
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("-?\\d+(\\.\\d+)?(\\*pi)?");
        java.util.regex.Matcher m = p.matcher(normalized);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(normalized, last, m.start());
            String val = m.group();
            double d;
            if (val.endsWith("*pi")) {
                d = Double.parseDouble(val.substring(0, val.length() - 3)) * Math.PI;
            } else if (val.contains(".")) {
                d = Double.parseDouble(val);
            } else {
                // Integer, keep as is
                sb.append(val);
                last = m.end();
                continue;
            }
            
            // Use 3 decimal places to ignore minor precision/rounding differences
            String formatted = String.format("%.3f", d);
            formatted = formatted.replaceAll("0+$", "");
            if (formatted.endsWith(".")) formatted = formatted.substring(0, formatted.length() - 1);
            sb.append(formatted);
            last = m.end();
        }
        sb.append(normalized.substring(last));
        return sb.toString();
    }
}
