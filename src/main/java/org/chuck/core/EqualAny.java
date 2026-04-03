package org.chuck.core;

import org.chuck.core.instr.LogicInstrs;

/**
 * Compares two top elements of the stack for equality.
 */
public class EqualAny implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
        if (s.reg.getSp() < 2) {
            s.reg.pop();
            s.reg.push(0L);
            return;
        }
        
        boolean equal = false;
        if (s.reg.isObject(0) && s.reg.isObject(1)) {
            Object r = s.reg.popObject();
            Object l = s.reg.popObject();
            if (l == r) equal = true;
            else if (l == null || r == null) equal = false;
            else equal = l.toString().equals(r.toString());
        } else if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
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
