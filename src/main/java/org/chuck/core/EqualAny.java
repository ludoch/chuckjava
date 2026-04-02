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
        if (s.reg.isObject(0) && s.reg.isObject(1)) {
            Object o1 = s.reg.popObject();
            Object o2 = s.reg.popObject();
            if (o1 == o2) equal = true;
            else if (o1 == null || o2 == null) equal = false;
            else equal = o1.toString().equals(o2.toString());
        } else if (s.reg.isDouble(0) || s.reg.isDouble(1) || s.reg.isObject(0) || s.reg.isObject(1)) {
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
