package org.chuck.core;

/**
 * Duplicates the top element of the ChucK stack.
 */
public class Duplicate implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
        int sp = s.reg.getSp();
        if (sp == 0) throw new org.chuck.core.ChuckRuntimeException("ChucK stack underflow on Duplicate");
        
        int topRef = sp - 1;
        if (s.reg.isObjectAt(topRef)) {
            s.reg.pushObject(s.reg.getRef(topRef));
        } else if (s.reg.isDoubleAt(topRef)) {
            double val = Double.longBitsToDouble(s.reg.getData(topRef));
            s.reg.push(val);
        } else {
            s.reg.push(s.reg.getData(topRef));
        }
    }
}
