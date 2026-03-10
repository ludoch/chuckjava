package org.chuck.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ChuckStdTest {

    @Test
    public void testMtof() {
        // MIDI note 69 is A4 (440 Hz)
        assertEquals(440.0, Std.mtof(69.0), 1e-4);
        
        // MIDI note 60 is Middle C (261.625 Hz)
        assertEquals(261.6255, Std.mtof(60.0), 1e-4);
    }

    @Test
    public void testFtom() {
        // Inverse operations
        assertEquals(69.0, Std.ftom(440.0), 1e-4);
        assertEquals(60.0, Std.ftom(261.6255), 1e-4);
    }

    @Test
    public void testStdMtofInstruction() {
        ChuckVM vm = new ChuckVM(44100);
        ChuckCode code = new ChuckCode("TestMtof");
        code.addInstruction(new PushFloat(69.0));
        code.addInstruction(new StdMtof());

        ChuckShred shred = new ChuckShred(code);
        vm.spork(shred);
        
        // Run shred
        vm.advanceTime(1);
        
        assertTrue(shred.isDone());
        // Verify result is on stack
        assertEquals(440.0, shred.reg.popDouble(), 1e-4);
    }
}
