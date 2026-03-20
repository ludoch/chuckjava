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
        
        if (shred.reg.isObject(0)) {
            Object obj = shred.reg.popObject();
            if (obj instanceof ChuckDuration cd) {
                shred.yield(cd.samples());
            } else if (obj instanceof ChuckEvent event) {
                event.waitOn(shred, vm);
            }
            shred.reg.push(vm.getCurrentTime());
            return;
        }
        
        long samples = shred.reg.popAsLong();
        shred.yield(samples);
        shred.reg.push(vm.getCurrentTime());
    }
}
