package org.chuck.core;

/**
 * Returns from a user-defined method call.
 * Like ReturnFunc but also pops the 'this' UserObject from the shred's thisStack.
 */
public class ReturnMethod implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        int oldPc = (int) shred.mem.popLong();
        ChuckCode oldCode = (ChuckCode) shred.mem.popObject();
        shred.setCode(oldCode);
        shred.setPc(oldPc);
        shred.thisStack.poll(); // pop 'this'
    }
}
