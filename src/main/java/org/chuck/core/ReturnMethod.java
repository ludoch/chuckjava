package org.chuck.core;

/**
 * Returns from a user-defined method call.
 * Like ReturnFunc but also pops the 'this' UserObject from the shred's thisStack.
 */
public class ReturnMethod implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        // Like ReturnFunc but also pops 'this'
        if (shred.mem.getSp() == 0) {
            shred.abort();
            return;
        }
        
        long retPrim = 0L;
        Object retObj = null;
        boolean retIsDouble = false;
        
        int currentFP = shred.getFramePointer();
        int savedRegSp = (int) shred.mem.getData(currentFP - 1);
        if (shred.reg.getSp() > savedRegSp) {
            retIsDouble = shred.reg.isDouble(0);
            retPrim = shred.reg.peekLong(0);
            retObj = shred.reg.peekObject(0);
        }

        shred.reg.setSp(savedRegSp);

        int savedFP = (int) shred.mem.getData(currentFP - 2);
        int oldPc = (int) shred.mem.getData(currentFP - 3);
        ChuckCode oldCode = (ChuckCode) shred.mem.getRef(currentFP - 4);
        
        shred.setCode(oldCode);
        shred.setPc(oldPc);
        shred.setFramePointer(savedFP);
        shred.mem.setSp(currentFP - 4);

        if (retObj != null) shred.reg.pushObject(retObj);
        else if (retIsDouble) shred.reg.push(Double.longBitsToDouble(retPrim));
        else shred.reg.push(retPrim);

        shred.thisStack.pop(); // pop 'this'
    }
}
