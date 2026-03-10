package org.chuck.core;

/**
 * Instruction to wait on a ChucK Event (e.g., myEvent => now).
 */
public class WaitEvent implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        // The event object should be on the stack
        ChuckObject obj = (ChuckObject) shred.reg.popObject();
        if (obj instanceof ChuckEvent event) {
            event.waitOn(shred, vm);
        } else {
            throw new RuntimeException("Chucked non-event object to now");
        }
    }
}
