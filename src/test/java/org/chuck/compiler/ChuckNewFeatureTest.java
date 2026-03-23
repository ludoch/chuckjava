package org.chuck.compiler;

import org.chuck.core.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

public class ChuckNewFeatureTest {

    @Test
    public void testMeId() throws InterruptedException {
        String code = "me.id() => int id; <<< id >>>;";
        
        ChuckLexer lexer = new ChuckLexer(code);
        ChuckParser parser = new ChuckParser(lexer.tokenize());
        ChuckEmitter emitter = new ChuckEmitter();
        ChuckCode bytecode = emitter.emit(parser.parse(), "MeTest");
        
        ChuckVM vm = new ChuckVM(44100);
        List<String> output = Collections.synchronizedList(new ArrayList<>());
        vm.addPrintListener(output::add);
        
        ChuckShred shred = new ChuckShred(bytecode);
        int expectedId = shred.getId();
        vm.spork(shred);
        
        Thread.sleep(200);
        vm.advanceTime(10);
        
        assertTrue(shred.isDone());
        assertEquals(1, output.size());
        // ChucK long printing might be exact or double-based depending on pop()
        assertTrue(output.get(0).contains(String.valueOf(expectedId)));
    }

    @Test
    public void testSwapOperator() throws InterruptedException {
        String code = "10 => int a; 20 => int b; a <=> b; <<< a, b >>>;";
        
        ChuckLexer lexer = new ChuckLexer(code);
        ChuckParser parser = new ChuckParser(lexer.tokenize());
        ChuckEmitter emitter = new ChuckEmitter();
        ChuckCode bytecode = emitter.emit(parser.parse(), "SwapTest");
        
        ChuckVM vm = new ChuckVM(44100);
        List<String> output = Collections.synchronizedList(new ArrayList<>());
        vm.addPrintListener(output::add);
        
        ChuckShred shred = new ChuckShred(bytecode);
        vm.spork(shred);
        
        Thread.sleep(100);
        vm.advanceTime(1);
        
        assertTrue(shred.isDone());
        assertEquals(1, output.size());
        assertEquals("20 10", output.get(0));
    }
}
