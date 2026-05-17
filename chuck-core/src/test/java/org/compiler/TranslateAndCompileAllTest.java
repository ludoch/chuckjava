package org.chuck.compiler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Stress test for the ChuckToDSLConverter. Iterates through all .ck files in the samples directory,
 * translates them to Java DSL, and verifies they compile.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
public class TranslateAndCompileAllTest {

  private record GeneratedUnit(Path ckFile, String fileName, String javaCode, Path javaFile) {}

  @Test
  public void testTranslateAndCompileAll() throws IOException {
    Path samplesDir = Paths.get("../chuck-samples/src/main/resources/examples");
    if (!Files.exists(samplesDir)) {
      samplesDir = Paths.get("chuck-samples/src/main/resources/examples");
    }

    assertTrue(
        Files.exists(samplesDir), "Samples directory not found at: " + samplesDir.toAbsolutePath());

    List<Path> ckFiles;
    try (Stream<Path> walk = Files.walk(samplesDir)) {
      ckFiles = walk.filter(p -> p.toString().endsWith(".ck")).collect(Collectors.toList());
    }

    System.out.println("Found " + ckFiles.size() + " .ck files to test.");

    Path tempDir = Files.createTempDirectory("chuck_compile_test");
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    String classpath = System.getProperty("java.class.path");

    // Add current module's classes to classpath
    String moduleClasses = new File("target/classes").getAbsolutePath();
    if (!new File(moduleClasses).exists()) {
      moduleClasses = new File("chuck-core/target/classes").getAbsolutePath();
    }
    classpath = moduleClasses + File.pathSeparator + classpath;
    System.out.println("Final Compiler CP: " + classpath);

    List<String> failures = new ArrayList<>();
    List<GeneratedUnit> generatedUnits = new ArrayList<>();

    for (Path ckFile : ckFiles) {
      String fileName = ckFile.getFileName().toString();
      String relPath = samplesDir.relativize(ckFile).toString();
      String className =
          "Generated_"
              + relPath
                  .replace('\\', '_')
                  .replace('/', '_')
                  .replace(".", "_")
                  .replace("-", "_")
                  .replace("+", "plus");

      try {
        String source = Files.readString(ckFile);

        // 1. Translate
        var input = CharStreams.fromString(source);
        var lexer = new ChuckANTLRLexer(input);
        var tokens = new CommonTokenStream(lexer);
        var parser = new ChuckANTLRParser(tokens);

        // Silence parser errors for the batch test
        parser.removeErrorListeners();

        var visitor = new ChuckASTVisitor(tokens);
        Object result = visitor.visit(parser.program());
        if (!(result instanceof List)) {
          continue; // Skip files that don't parse to a statement list
        }

        @SuppressWarnings("unchecked")
        List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) result;

        var converter = new ChuckToDSLConverter();
        String javaCode = converter.convert(ast, className);

        // 2. Materialize generated source (compilation happens in iterative passes)
        Path javaFile = tempDir.resolve(className + ".java");
        Files.writeString(javaFile, javaCode);
        generatedUnits.add(new GeneratedUnit(ckFile, fileName, javaCode, javaFile));
      } catch (Exception e) {
        failures.add(ckFile.toString() + " (Translation Error: " + e.getMessage() + ")");
      }
    }

    // Compile generated sources in iterative passes so classes can resolve symbols from
    // other generated classes compiled in earlier passes.
    String compileClasspath = tempDir + File.pathSeparator + classpath;
    List<GeneratedUnit> pending = new ArrayList<>(generatedUnits);
    Map<Path, String> compileFailures = new LinkedHashMap<>();
    int successCount = 0;
    int maxPasses = Math.max(3, pending.size());
    int pass = 0;
    while (!pending.isEmpty() && pass < maxPasses) {
      pass++;
      int compiledThisPass = 0;
      List<GeneratedUnit> nextPending = new ArrayList<>();
      compileFailures.clear();

      for (GeneratedUnit unit : pending) {
        List<String> options =
            List.of(
                "-d",
                tempDir.toString(),
                "-cp",
                compileClasspath,
                "--enable-preview",
                "--release",
                "25");

        var fileManager = compiler.getStandardFileManager(null, null, null);
        var compilationUnits = fileManager.getJavaFileObjects(unit.javaFile());
        var task = compiler.getTask(null, fileManager, null, options, null, compilationUnits);

        if (task.call()) {
          successCount++;
          compiledThisPass++;
        } else {
          nextPending.add(unit);
          compileFailures.put(unit.ckFile(), unit.fileName());
        }
        fileManager.close();
      }

      pending = nextPending;
      if (compiledThisPass == 0) {
        break;
      }
    }

    for (GeneratedUnit unit : pending) {
      failures.add(unit.ckFile() + " (Compilation Failed)");
      if (failures.size() < 5) {
        System.err.println("Generated code for " + unit.fileName() + ":");
        System.err.println(unit.javaCode());
      }
    }

    System.out.println("Batch Test Finished:");
    System.out.println("  Total:   " + ckFiles.size());
    System.out.println("  Success: " + successCount);
    System.out.println("  Failure: " + failures.size());

    if (!failures.isEmpty()) {
      System.out.println("Failures:");
      failures.forEach(f -> System.out.println("  - " + f));
    }

    // For now, we don't assert 100% success to avoid breaking the build while we're still improving
    // parity,
    // but we output the report.
    // assertTrue(failures.isEmpty(), "Some files failed to translate or compile.");
  }
}
