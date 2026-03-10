package org.chuck.core;

/**
 * Advances time by the duration (in samples).
 */
public class AdvanceTime implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        long samples = shred.reg.popLong();
        shred.yield(samples);
    }
}
