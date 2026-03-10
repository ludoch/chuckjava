package org.chuck.core;

/**
 * Returns from a function call.
 * Restores the previous PC and Code from the mem stack.
 */
public class ReturnFunc implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        // Restore state from mem stack
        int oldPc = (int) shred.mem.popLong();
        ChuckCode oldCode = (ChuckCode) shred.mem.popObject();
        
        shred.setCode(oldCode);
        shred.setPc(oldPc); // No increment here? 
        // In the main loop, pc++ happens after execute.
        // So we restore the pc that was about to be incremented.
    }
}
