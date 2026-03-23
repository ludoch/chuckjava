package org.chuck.core;

/**
 * Returns from a function call.
 * Restores the previous PC, Code, and reg stack pointer from the mem stack,
 * discarding any leaked arguments and preserving the return value (if any).
 */
public class ReturnFunc implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        // Restore state from mem stack (order mirrors CallFunc push order, reversed)
        // mem: [..., SavedCode, SavedPC, SavedFP, SavedRegSP, Arg0, Arg1, ... ]
        // current FP points to where Arg0 is.
        int fp = shred.getFramePointer();
        if (fp < 4) { shred.abort(); return; }

        int savedRegSp = (int) shred.mem.getData(fp - 1);
        int savedFP    = (int) shred.mem.getData(fp - 2);
        int savedPc    = (int) shred.mem.getData(fp - 3);
        ChuckCode savedCode = (ChuckCode) shred.mem.getRef(fp - 4);

        // Return value preservation (if any)
        long retPrim = 0L;
        Object retObj = null;
        boolean retIsDouble = false;
        boolean hasReturn = shred.reg.getSp() > savedRegSp;
        
        if (hasReturn) {
            retIsDouble = shred.reg.isDouble(0);
            if (shred.reg.isObject(0)) retObj = shred.reg.popObject();
            else if (retIsDouble) retPrim = Double.doubleToRawLongBits(shred.reg.popDouble());
            else retPrim = shred.reg.popLong();
        }

        // Restore
        shred.reg.setSp(savedRegSp);
        shred.setCode(savedCode);
        shred.setPc(savedPc);
        shred.setFramePointer(savedFP);
        shred.mem.setSp(fp - 4);
        

        // Push return value back onto reg stack
        if (hasReturn) {
            if (retObj != null) shred.reg.pushObject(retObj);
            else if (retIsDouble) shred.reg.push(Double.longBitsToDouble(retPrim));
            else shred.reg.push(retPrim);
        }
    }
}
