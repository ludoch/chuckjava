package org.chuck;

import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.*;
import org.chuck.compiler.*;

/** Batch test utility to verify the ANTLR4 parser against all example files. */
public class AntlrExamplesTester {
  private static final Logger logger = Logger.getLogger(AntlrExamplesTester.class.getName());

  public static void main(String[] args) {
    Path examplesPath = Paths.get("examples");
    if (!Files.exists(examplesPath)) {
      logger.severe("Examples directory not found!");
      return;
    }

    logger.info("🔍 Starting ANTLR4 Batch Test on all examples...");

    try {
      List<Path> ckFiles =
          Files.walk(examplesPath)
              .filter(p -> p.toString().endsWith(".ck"))
              .collect(Collectors.toList());

      logger.info("Found " + ckFiles.size() + " .ck files.");

      int passed = 0;
      int failed = 0;
      int expectedFailures = 0;
      int unexpectedPasses = 0;
      List<String> failureDetails = new ArrayList<>();

      for (Path file : ckFiles) {
        String pathStr = file.toString().replace('\\', '/');
        String fileName = file.getFileName().toString();
        boolean isErrorTest =
            pathStr.contains("06-Errors")
                || fileName.contains("error-")
                || fileName.equals("114.ck");
        boolean isDisabled = pathStr.contains(".disabled") || pathStr.contains(".deps");
        boolean shouldFail = isErrorTest || isDisabled;

        String testInfo = "Testing: " + pathStr + (shouldFail ? " [Expected Failure]" : "");
        try {
          String source = Files.readString(file);

          // 1. Lex & Parse
          CharStream input = CharStreams.fromString(source);
          ChuckANTLRLexer lexer = new ChuckANTLRLexer(input);
          // Add error listener to catch lexer/parser errors as exceptions
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

          // 2. Visit (Map to AST)
          ChuckASTVisitor visitor = new ChuckASTVisitor();
          @SuppressWarnings("unchecked")
          List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());

          // 3. Emit (Optional but good for full check)
          ChuckEmitter emitter = new ChuckEmitter();
          emitter.emit(ast, fileName);

          if (shouldFail) {
            logger.info(testInfo + " ... ❌ FAILED (Expected failure but passed)");
            failed++;
            unexpectedPasses++;
            failureDetails.add(pathStr + ": Expected failure but passed");
          } else {
            logger.info(testInfo + " ... ✅ PASSED");
            passed++;
          }
        } catch (Exception e) {
          if (shouldFail) {
            logger.info(testInfo + " ... ✅ PASSED (Expected failure)");
            passed++;
            expectedFailures++;
          } else {
            logger.info(testInfo + " ... ❌ FAILED");
            failed++;
            failureDetails.add(pathStr + ": " + e.getMessage());
          }
        }
      }

      logger.info("\n--- Batch Test Results ---");
      logger.info("TOTAL:             " + ckFiles.size());
      logger.info(
          "PASSED:            " + passed + " (incl. " + expectedFailures + " expected failures)");
      logger.info(
          "FAILED:            " + failed + " (incl. " + unexpectedPasses + " unexpected passes)");

      if (failed > 0) {
        logger.info("\nFailure Details:");
        failureDetails.forEach(logger::info);
      }

    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error during batch test", e);
    }
  }
}
