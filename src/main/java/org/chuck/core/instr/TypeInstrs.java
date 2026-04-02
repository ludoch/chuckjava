package org.chuck.core.instr;

import org.chuck.core.*;

public class TypeInstrs {
    public static class CastToInt implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.push(s.reg.popAsLong()); }
    }

    public static class CastToFloat implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.push(s.reg.popAsDouble()); }
    }

    public static class CastToString implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object o = s.reg.pop();
            s.reg.pushObject(new ChuckString(String.valueOf(o)));
        }
    }

    public static class EnsureFloat implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (!s.reg.isDouble(0)) {
                double val = (double) s.reg.popLong();
                s.reg.push(val);
            }
        }
    }
}
