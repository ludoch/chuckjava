package org.chuck.core;

/**
 * Instruction for calling Std.mtof (MIDI to Frequency).
 */
public class StdMtof implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        if (shred.reg.getSp() == 0) return;
        double midi = shred.reg.popAsDouble();
        double freq = Std.mtof(midi);
        shred.reg.push(freq);
    }
}
