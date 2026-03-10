package org.chuck.compiler;

import org.chuck.core.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ChuckExampleTest {

    @Test
    public void testADSRScript() throws InterruptedException {
        // A subset of examples/basic/adsr.ck
        String codeSource = """
            100 => s.freq;
            while (true) {
                10 => now;
            }
            """;
        
        ChuckLexer lexer = new ChuckLexer(codeSource);
        List<ChuckLexer.Token> tokens = lexer.tokenize();
        
        ChuckParser parser = new ChuckParser(tokens);
        List<ChuckAST.Stmt> ast = parser.parse();
        
        ChuckEmitter emitter = new ChuckEmitter();
        ChuckCode bytecode = emitter.emit(ast, "ADSRDemo");
        
        ChuckVM vm = new ChuckVM(44100);
        
        // Mock a "s" object (SinOsc)
        ChuckType sinType = new ChuckType("SinOsc", ChuckType.OBJECT, 2, 0);
        ChuckObject sinOsc = new ChuckObject(sinType);
        vm.setGlobalInt("s", 0); // We'll represent objects by IDs or direct globals in this simple test
        
        // Let's manually push the object for the script to use
        ChuckShred shred = new ChuckShred(bytecode) {
            @Override
            public void execute(ChuckVM vm) {
                // Pre-populate "s" in the shred's environment or global VM
                vm.setGlobalInt("s_ptr", 12345); // Fake pointer
                super.execute(vm);
            }
        };
        
        // In this demo, we'll just verify the parser/emitter handle the script without error
        assertNotNull(bytecode);
        assertTrue(bytecode.getNumInstructions() > 0);
    }

    @Test
    public void testMidiLoop() throws InterruptedException {
        // A standard MIDI loop in ChucK
        String codeSource = """
            MidiIn min;
            min.open(0);
            while (true) {
                min => now;
                midi_b1 => Std.mtof => s.freq;
            }
            """;
        
        ChuckLexer lexer = new ChuckLexer(codeSource);
        List<ChuckLexer.Token> tokens = lexer.tokenize();
        ChuckParser parser = new ChuckParser(tokens);
        List<ChuckAST.Stmt> ast = parser.parse();
        
        ChuckEmitter emitter = new ChuckEmitter();
        ChuckCode bytecode = emitter.emit(ast, "MidiLoop");
        
        assertNotNull(bytecode);
        assertTrue(bytecode.getNumInstructions() > 0);
    }
}
