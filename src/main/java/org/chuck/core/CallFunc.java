package org.chuck.core;

/**
 * Instruction to call a ChucK function.
 * Pushes the current PC and Code onto the mem stack.
 */
public class CallFunc implements ChuckInstr {
    private final ChuckCode targetCode;

    public CallFunc(ChuckCode targetCode) {
        this.targetCode = targetCode;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        // Save current state on mem stack
        shred.mem.pushObject(shred.getCode());
        shred.mem.push(shred.getPc());
        
        // Switch to target code
        shred.setCode(targetCode);
        shred.setPc(-1); // Will be incremented to 0 by the loop
    }
}
