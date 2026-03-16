package org.chuck.core;

/**
 * Advances time by the duration (in samples).
 * Pushes the new value of 'now' (in samples) after yielding,
 * so expressions like "while(1::ms => now)" evaluate as truthy.
 */
public class AdvanceTime implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        if (shred.reg.getSp() == 0) return;
        long samples = shred.reg.popLong();
        shred.yield(samples);
        shred.reg.push(vm.getCurrentTime());
    }
}
