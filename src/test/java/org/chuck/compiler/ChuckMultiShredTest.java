package org.chuck.compiler;

import org.chuck.core.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

public class ChuckMultiShredTest {

    @Test
    public void testMultiShredDeterministicTiming() throws InterruptedException {
        // This script sporks two shreds that increment different globals
        String codeSource = """
            fun void foo() {
                while (true) {
                    foo_count + 1 => foo_count;
                    10 => now;
                }
            }
            
            fun void bar() {
                while (true) {
                    bar_count + 1 => bar_count;
                    5 => now;
                }
            }
            
            spork ~ foo();
            spork ~ bar();
            
            30 => now;
            """;
        
        ChuckLexer lexer = new ChuckLexer(codeSource);
        List<ChuckLexer.Token> tokens = lexer.tokenize();
        
        ChuckParser parser = new ChuckParser(tokens);
        List<ChuckAST.Stmt> ast = parser.parse();
        
        ChuckEmitter emitter = new ChuckEmitter();
        ChuckCode bytecode = emitter.emit(ast, "Main");
        
        ChuckVM vm = new ChuckVM(44100);
        vm.setGlobalInt("foo_count", 0);
        vm.setGlobalInt("bar_count", 0);
        
        ChuckShred mainShred = new ChuckShred(bytecode);
        vm.spork(mainShred);
        
        // Advance time by 30 samples.
        // Advance time by 31 samples to include T=30 execution.
        // foo runs at T=0, 10, 20, 30. (Total 4 increments)
        // bar runs at T=0, 5, 10, 15, 20, 25, 30. (Total 7 increments)
        
        Thread.sleep(200); // Give virtual threads time
        vm.advanceTime(31);
        
        // Allow a bit more time for the final wakeups
        Thread.sleep(100);
        
        assertEquals(4, vm.getGlobalInt("foo_count"));
        assertEquals(7, vm.getGlobalInt("bar_count"));
        assertTrue(mainShred.isDone());
    }
}
