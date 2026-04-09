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

    public static class Dup2 implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.dup2(); }
    }

    public static class Rot implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.rot(); }
    }

    public static class Swap implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.swap(); }
    }

    public static class PushThis implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object obj = s.thisStack.isEmpty() ? null : s.thisStack.peek();
            s.reg.pushObject(obj);
        }
    }

    public static class PeekStack implements ChuckInstr {
        int depth; public PeekStack(int d) { depth = d; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isObject(depth)) s.reg.pushObject(s.reg.peekObject(depth));
            else if (s.reg.isDouble(depth)) s.reg.push(s.reg.peekAsDouble(depth));
            else s.reg.push(s.reg.peekLong(depth));
        }
    }
}
