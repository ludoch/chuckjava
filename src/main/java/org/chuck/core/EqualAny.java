package org.chuck.core;

/**
 * Compares two top elements of the stack for equality.
 */
public class EqualAny implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
        int sp = s.reg.getSp();
        if (sp < 2) throw new RuntimeException("ChucK stack underflow on EqualAny");
        
        boolean isObj1 = s.reg.isObjectAt(sp - 1);
        boolean isDouble1 = s.reg.isDoubleAt(sp - 1);
        
        boolean isObj2 = s.reg.isObjectAt(sp - 2);
        boolean isDouble2 = s.reg.isDoubleAt(sp - 2);
        
        boolean equal = false;
        if (isObj1 && isObj2) {
            Object o1 = s.reg.popObject();
            Object o2 = s.reg.popObject();
            equal = (o1 == o2 || (o1 != null && o1.equals(o2)));
        } else if (isDouble1 || isDouble2) {
            double v1 = s.reg.popAsDouble();
            double v2 = s.reg.popAsDouble();
            equal = (v1 == v2);
        } else {
            long v1 = s.reg.popAsLong();
            long v2 = s.reg.popAsLong();
            equal = (v1 == v2);
        }
        s.reg.push(equal ? 1L : 0L);
    }
}
