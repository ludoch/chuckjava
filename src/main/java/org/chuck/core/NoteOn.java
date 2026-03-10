package org.chuck.core;

import org.chuck.audio.ChuckUGen;
import java.lang.reflect.Method;

/**
 * Instruction to call noteOn() on a UGen.
 */
public class NoteOn implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        // Stack: [Velocity, Object]
        ChuckObject obj = (ChuckObject) shred.reg.popObject();
        float velocity = (float) Double.longBitsToDouble(shred.reg.popLong());
        
        if (obj == null) throw new RuntimeException("NoteOn: target is null");
        
        try {
            // Use reflection to call noteOn if it exists
            Method m = obj.getClass().getMethod("noteOn", float.class);
            m.invoke(obj, velocity);
        } catch (Exception e) {
            // Fallback for simple UGens
        }
    }
}
