package org.chuck.compiler;

import org.chuck.core.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

public class ChuckComplexScriptTest {

    @Test
    public void testAlgorithmicLoop() throws InterruptedException {
        // This script plays a countdown of frequencies
        String codeSource = """
            3 => int N;
            while (N > 0) {
                60 + N => Std.mtof => s.freq;
                10 => now;
                N - 1 => N;
            }
            """;
        
        ChuckLexer lexer = new ChuckLexer(codeSource);
        List<ChuckLexer.Token> tokens = lexer.tokenize();
        
        ChuckParser parser = new ChuckParser(tokens);
        List<ChuckAST.Stmt> ast = parser.parse();
        
        ChuckEmitter emitter = new ChuckEmitter();
        ChuckCode bytecode = emitter.emit(ast, "LoopScript");
        
        ChuckVM vm = new ChuckVM(44100);
        
        // Mock SineWave "s"
        ChuckType sinType = new ChuckType("SinOsc", ChuckType.OBJECT, 2, 0);
        List<Double> frequencies = Collections.synchronizedList(new ArrayList<>());
        ChuckObject sinOsc = new ChuckObject(sinType) {
            @Override
            public void setData(int index, double value) {
                if (index == 0) frequencies.add(value);
                super.setData(index, value);
            }
        };
        vm.setGlobalObject("s", sinOsc);

        ChuckShred shred = new ChuckShred(bytecode);
        vm.spork(shred);
        
        // Run the VM
        Thread.sleep(100);
        vm.advanceTime(50); 
        
        assertTrue(shred.isDone());
        
        // N starts at 3. Loop runs for N=3, 2, 1.
        assertEquals(3, frequencies.size());
        assertEquals(Std.mtof(63.0), frequencies.get(0), 1e-4);
        assertEquals(Std.mtof(62.0), frequencies.get(1), 1e-4);
        assertEquals(Std.mtof(61.0), frequencies.get(2), 1e-4);
        }
        @Test
        public void testPentatonicScale() throws InterruptedException {
        // Pentatonic major intervals: 0, 2, 4, 7, 9
        String codeSource = """
            [0, 2, 4, 7, 9] @=> int intervals[];
            60 => int root;
            for (0 => int i; i < 5; 1 + i => i) {
                root + intervals[i] => Std.mtof => s.freq;
                10 => now;
            }
            """;

        ChuckLexer lexer = new ChuckLexer(codeSource);
        List<ChuckLexer.Token> tokens = lexer.tokenize();
        ChuckParser parser = new ChuckParser(tokens);
        List<ChuckAST.Stmt> ast = parser.parse();

        ChuckEmitter emitter = new ChuckEmitter();
        ChuckCode bytecode = emitter.emit(ast, "Pentatonic");

        ChuckVM vm = new ChuckVM(44100);
        ChuckType sinType = new ChuckType("SinOsc", ChuckType.OBJECT, 2, 0);
        List<Double> frequencies = Collections.synchronizedList(new ArrayList<>());
        ChuckObject sinOsc = new ChuckObject(sinType) {
            @Override
            public void setData(int index, double value) {
                if (index == 0) frequencies.add(value);
                super.setData(index, value);
            }
        };
        vm.setGlobalObject("s", sinOsc);

        ChuckShred shred = new ChuckShred(bytecode);
        vm.spork(shred);

        Thread.sleep(100);
        vm.advanceTime(100);

        assertTrue(shred.isDone());
        assertEquals(5, frequencies.size());
        assertEquals(Std.mtof(60.0), frequencies.get(0), 1e-4);
        assertEquals(Std.mtof(62.0), frequencies.get(1), 1e-4);
        assertEquals(Std.mtof(64.0), frequencies.get(2), 1e-4);
        assertEquals(Std.mtof(67.0), frequencies.get(3), 1e-4);
        assertEquals(Std.mtof(69.0), frequencies.get(4), 1e-4);
        }

        @Test
        public void testArrayDeclaration() throws InterruptedException {

        String codeSource = """
            int scale[3];
            60 => scale[0];
            64 => scale[1];
            67 => scale[2];
            """;
        
        ChuckLexer lexer = new ChuckLexer(codeSource);
        List<ChuckLexer.Token> tokens = lexer.tokenize();
        ChuckParser parser = new ChuckParser(tokens);
        List<ChuckAST.Stmt> ast = parser.parse();
        
        ChuckEmitter emitter = new ChuckEmitter();
        ChuckCode bytecode = emitter.emit(ast, "ArrayDeclTest");
        
        ChuckVM vm = new ChuckVM(44100);
        ChuckShred shred = new ChuckShred(bytecode);
        vm.spork(shred);
        
        Thread.sleep(100);
        vm.advanceTime(1);
        
        assertTrue(shred.isDone());
        
        // Verify array contents in VM
        ChuckArray arr = (ChuckArray) vm.getGlobalObject("scale");
        assertNotNull(arr);
        assertEquals(3, arr.size());
        assertEquals(60, arr.getInt(0));
        assertEquals(64, arr.getInt(1));
        assertEquals(67, arr.getInt(2));
    }
}
