package org.chuck.compiler;

import org.chuck.core.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ChuckFullCompilerTest {

    @Test
    public void testEndToEndCompilation() throws InterruptedException {
        // A simple ChucK program that adds 1+2 and then yields for 10 samples
        String codeSource = "1 + 2 => now;";
        
        // 1. Lex
        ChuckLexer lexer = new ChuckLexer(codeSource);
        List<ChuckLexer.Token> tokens = lexer.tokenize();
        
        // 2. Parse
        ChuckParser parser = new ChuckParser(tokens);
        List<ChuckAST.Stmt> ast = parser.parse();
        
        // 3. Emit
        ChuckEmitter emitter = new ChuckEmitter();
        ChuckCode bytecode = emitter.emit(ast, "SimpleProgram");
        
        // 4. Verify Bytecode (expected: [PushInt(1), PushInt(2), AddInt(), AdvanceTime(), Pop()])
        assertEquals(6, bytecode.getNumInstructions());
        
        // 5. Run in VM
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
