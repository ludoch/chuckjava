package org.chuck.core;

/**
 * Returns from a function call.
 * Restores the previous PC, Code, and reg stack pointer from the mem stack,
 * discarding any leaked arguments and preserving the return value (if any).
 */
public class ReturnFunc implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        // If mem stack is empty, this is a sporked root function — just terminate.
        if (shred.mem.getSp() == 0) {
            shred.abort();
            return;
        }
        
        // Return value preservation (if any)
        long retPrim = 0L;
        Object retObj = null;
        boolean retIsDouble = false;
        
        // Restore state from mem stack (order mirrors CallFunc push order, reversed)
        // [SavedCode, SavedPC, SavedFP, SavedRegSP, Arg0, Arg1, ... ]
        // The frame pointer is at the position saved by CallFunc.
        int currentFP = shred.getFramePointer();
        
        // Capture return value from top of reg stack if it was pushed AFTER savedRegSp
        int savedRegSp = (int) shred.mem.getData(currentFP - 1);
        if (shred.reg.getSp() > savedRegSp) {
            retIsDouble = shred.reg.isDouble(0);
            retPrim = shred.reg.peekLong(0);
            retObj = shred.reg.peekObject(0);
        }

        // Restore reg sp to before-args position
        shred.reg.setSp(savedRegSp);

        // Restore VM state
        int savedFP = (int) shred.mem.getData(currentFP - 2);
        int oldPc = (int) shred.mem.getData(currentFP - 3);
        ChuckCode oldCode = (ChuckCode) shred.mem.getRef(currentFP - 4);
        
        shred.setCode(oldCode);
        shred.setPc(oldPc);
        shred.setFramePointer(savedFP);
        
        // Shrink mem stack to before the frame
        shred.mem.setSp(currentFP - 4);

        // Push return value back onto reg stack
        if (retObj != null) {
            shred.reg.pushObject(retObj);
        } else if (retIsDouble) {
            shred.reg.push(Double.longBitsToDouble(retPrim));
        } else {
            shred.reg.push(retPrim);
        }
    }
}
