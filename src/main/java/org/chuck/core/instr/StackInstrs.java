package org.chuck.core.instr;

import org.chuck.core.ChuckInstr;
import org.chuck.core.ChuckVM;
import org.chuck.core.ChuckShred;

public class StackInstrs {
    public static class Pop implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pop(); }
    }

    public static class Dup implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.dup(); }
    }

    public static class Swap implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.swap(); }
    }

    public static class PushThis implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.pushObject(s.thisStack.isEmpty() ? null : s.thisStack.peek());
        }
    }
}
