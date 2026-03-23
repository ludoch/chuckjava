package org.chuck.core;

import java.lang.reflect.Method;

/**
 * Instruction to call noteOn() on a UGen.
 */
public class NoteOn implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        // Stack: [Velocity, Object]
        if (shred.reg.getSp() < 2) return; // not enough values on stack
        ChuckObject obj = (ChuckObject) shred.reg.popObject();
        float velocity = (float) Double.longBitsToDouble(shred.reg.popLong());

        if (obj == null) return; // silently skip if target not yet wired
        
        try {
            // Use reflection to call noteOn if it exists
            Method m = obj.getClass().getMethod("noteOn", float.class);
            m.invoke(obj, velocity);
        } catch (Exception e) {
            // Fallback for simple UGens
        }
    }
}
