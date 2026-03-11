package org.chuck;

import org.chuck.compiler.*;
import org.chuck.core.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Batch test utility to verify the ORIGINAL handwritten parser against all example files.
 */
public class HandwrittenExamplesTester {
    public static void main(String[] args) {
        Path examplesPath = Paths.get("examples");
        if (!Files.exists(examplesPath)) {
            System.err.println("Examples directory not found!");
            return;
        }

        System.out.println("🔍 Starting Handwritten Parser Batch Test...");
        
        try {
            List<Path> ckFiles = Files.walk(examplesPath)
                .filter(p -> p.toString().endsWith(".ck"))
                .collect(Collectors.toList());

            System.out.println("Found " + ckFiles.size() + " .ck files.");
            
            int passed = 0;
            int failed = 0;
            List<String> failureDetails = new ArrayList<>();

            for (Path file : ckFiles) {
                try {
                    String source = Files.readString(file);
                    
                    // Use handwritten pipeline
                    ChuckLexer lexer = new ChuckLexer(source);
                    List<ChuckLexer.Token> tokens = lexer.tokenize();
                    ChuckParser parser = new ChuckParser(tokens);
                    List<ChuckAST.Stmt> ast = parser.parse();
                    
                    // Try to emit to ensure AST is valid for the engine
                    ChuckEmitter emitter = new ChuckEmitter();
                    emitter.emit(ast, file.getFileName().toString());

                    passed++;
                } catch (Exception e) {
                    failed++;
                    failureDetails.add(file + ": " + e.getMessage());
                }
            }

            System.out.println("\n--- Handwritten Batch Test Results ---");
            System.out.println("TOTAL:  " + ckFiles.size());
            System.out.println("PASSED: " + passed);
            System.out.println("FAILED: " + failed);
            System.out.printf("PASS RATE: %.2f%%\n", (passed / (double)ckFiles.size()) * 100);
            
            if (failed > 0) {
                System.out.println("\nTop 5 Failure Samples:");
                failureDetails.stream().limit(5).forEach(System.out::println);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
