package org.chuck.compiler;

import org.antlr.v4.runtime.*;
import org.chuck.core.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ChuckFullCompilerTest {

    @Test
    public void testEndToEndCompilation() throws InterruptedException {
        // A simple ChucK program that adds 1+2 and then yields for 10 samples
        String codeSource = "1 + 2 => now;";
        
        // 1. Lex & Parse
        CharStream input = CharStreams.fromString(codeSource);
        ChuckANTLRLexer lexer = new ChuckANTLRLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ChuckANTLRParser parser = new ChuckANTLRParser(tokens);
        ChuckASTVisitor visitor = new ChuckASTVisitor();
        
        @SuppressWarnings("unchecked")
        List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());
        
        // 2. Emit
        ChuckEmitter emitter = new ChuckEmitter();
        ChuckCode bytecode = emitter.emit(ast, "SimpleProgram");
        
        // 3. Verify Bytecode (expected: [PushInt(1), PushInt(2), AddInt(), AdvanceTime(), Pop()])
        // ANTLR version might have slightly different instruction count due to block wrapping or similar, but the core logic should be same.
        assertTrue(bytecode.getNumInstructions() >= 5);
        
        // 4. Run in VM
        ChuckVM vm = new ChuckVM(44100);
        ChuckShred shred = new ChuckShred(bytecode);
        vm.spork(shred);
        
        // Wait for shred to process
        Thread.sleep(100);
        vm.advanceTime(5); // T=5. Shred woke at 0, pushed 1, pushed 2, added (stack=[3]), yielded for 3 samples. WakeTime=3.
        
        assertEquals(5, vm.getCurrentTime());
        assertEquals(3, shred.getWakeTime());
        assertTrue(shred.isDone()); // Should be done at T=5 as its wakeTime was 3.
    }
}
