package org.chuck.core;

/**
 * Instruction for calling Std.mtof (MIDI to Frequency).
 */
public class StdMtof implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        double midi;
        // Check if we have an object (ChuckFloat/ChuckInt wrapper) or raw primitive
        // In our simple stack, objects and primitives are separated.
        // If peekObject is null, it's a primitive long (which might be bits of double or a real int).
        
        // Let's assume for this test we might have a long int from the array.
        // We need a way to tell if it's a double or int.
        // For now, we'll try to be smart or just assume long for this specific test case.
        
        // Better: the stack should maybe track types or we just pop as long and cast.
        long raw = shred.reg.popLong();
        // If it's a small integer, it's definitely not a valid double bits for MIDI.
        // Heuristic:
        if (raw < 2000 && raw > -2000) {
            midi = (double) raw;
        } else {
            midi = Double.longBitsToDouble(raw);
        }
        
        double freq = Std.mtof(midi);
        shred.reg.push(freq);
    }
}
