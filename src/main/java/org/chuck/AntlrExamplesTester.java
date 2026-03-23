package org.chuck;

import org.antlr.v4.runtime.*;
import org.chuck.compiler.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Batch test utility to verify the ANTLR4 parser against all example files.
 */
public class AntlrExamplesTester {
    public static void main(String[] args) {
        Path examplesPath = Paths.get("examples");
        if (!Files.exists(examplesPath)) {
            System.err.println("Examples directory not found!");
            return;
        }

        System.out.println("🔍 Starting ANTLR4 Batch Test on all examples...");
        
        try {
            List<Path> ckFiles = Files.walk(examplesPath)
                .filter(p -> p.toString().endsWith(".ck"))
                .collect(Collectors.toList());

            System.out.println("Found " + ckFiles.size() + " .ck files.");
            
            int passed = 0;
            int failed = 0;
            List<String> failureDetails = new ArrayList<>();

            for (Path file : ckFiles) {
                System.out.print("Testing: " + file + " ... ");
                try {
                    String source = Files.readString(file);
                    
                    // 1. Lex & Parse
                    CharStream input = CharStreams.fromString(source);
                    ChuckANTLRLexer lexer = new ChuckANTLRLexer(input);
                    // Add error listener to catch lexer/parser errors as exceptions
                    lexer.removeErrorListeners();
                    lexer.addErrorListener(new BaseErrorListener() {
                        @Override
                        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                            throw new RuntimeException("Lexer error at " + line + ":" + charPositionInLine + " - " + msg);
                        }
                    });

                    CommonTokenStream tokens = new CommonTokenStream(lexer);
                    ChuckANTLRParser parser = new ChuckANTLRParser(tokens);
                    parser.removeErrorListeners();
                    parser.addErrorListener(new BaseErrorListener() {
                        @Override
                        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                            throw new RuntimeException("Parser error at " + line + ":" + charPositionInLine + " - " + msg);
                        }
                    });

                    // 2. Visit (Map to AST)
                    ChuckASTVisitor visitor = new ChuckASTVisitor();
                    @SuppressWarnings("unchecked")
                    List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());
                    
                    // 3. Emit (Optional but good for full check)
                    ChuckEmitter emitter = new ChuckEmitter();
                    emitter.emit(ast, file.getFileName().toString());

                    System.out.println("✅ PASSED");
                    passed++;
                } catch (Exception e) {
                    System.out.println("❌ FAILED");
                    failed++;
                    failureDetails.add(file + ": " + e.getMessage());
                }
            }

            System.out.println("\n--- Batch Test Results ---");
            System.out.println("TOTAL:  " + ckFiles.size());
            System.out.println("PASSED: " + passed);
            System.out.println("FAILED: " + failed);
            
            if (failed > 0) {
                System.out.println("\nFailure Details:");
                failureDetails.forEach(System.out::println);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
