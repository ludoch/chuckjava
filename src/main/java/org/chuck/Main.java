package org.chuck;

import org.chuck.audio.*;
import org.chuck.compiler.*;
import org.chuck.core.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java-chuck [--antlr] <filename.ck>");
            return;
        }

        boolean useAntlr = false;
        String fileName = "";
        for (String arg : args) {
            if (arg.equals("--antlr")) useAntlr = true;
            else fileName = arg;
        }

        if (fileName.isEmpty()) {
            System.out.println("Usage: java-chuck [--antlr] <filename.ck>");
            return;
        }

        try {
            System.out.println("🎸 ChucK-Java (JDK 25) - Running: " + fileName + (useAntlr ? " (ANTLR4 Mode)" : ""));
            
            // 1. Read ChucK Source
            String source = Files.readString(Paths.get(fileName));
            
            // 2. Initialize VM and Audio
            int sampleRate = 44100;
            ChuckVM vm = new ChuckVM(sampleRate);
            vm.setAntlrEnabled(useAntlr);
            ChuckAudio audio = new ChuckAudio(vm, 512, 2, sampleRate);
            audio.start();
            
            // 3. Compile Pipeline
            List<ChuckAST.Stmt> ast;
            if (useAntlr) {
                org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromString(source);
                ChuckANTLRLexer lexer = new ChuckANTLRLexer(input);
                org.antlr.v4.runtime.CommonTokenStream tokens = new org.antlr.v4.runtime.CommonTokenStream(lexer);
                ChuckANTLRParser parser = new ChuckANTLRParser(tokens);
                ChuckASTVisitor visitor = new ChuckASTVisitor();
                ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());
            } else {
                ChuckLexer lexer = new ChuckLexer(source);
                ChuckParser parser = new ChuckParser(lexer.tokenize());
                ast = parser.parse();
            }
            
            ChuckEmitter emitter = new ChuckEmitter();
            ChuckCode bytecode = emitter.emit(ast, fileName);
            
            // 4. Execute
            System.out.println("🔊 Execution started...");
            ChuckShred mainShred = new ChuckShred(bytecode);
            vm.spork(mainShred);
            
            // Keep the application alive while the VM is running
            while (!mainShred.isDone()) {
                Thread.sleep(100);
            }
            
            audio.stop();
            System.out.println("✅ Finished.");
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
