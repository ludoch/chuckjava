package org.chuck.core.instr;

import org.chuck.core.*;

public class PushInstrs {
    public static class PushInt implements ChuckInstr {
        long v; public PushInt(long val) { v = val; }
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.push(v); }
    }

    public static class PushFloat implements ChuckInstr {
        double v; public PushFloat(double val) { v = val; }
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.push(v); }
    }

    public static class PushString implements ChuckInstr {
        String v; public PushString(String val) { v = val; }
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject(new ChuckString(v)); }
    }

    public static class PushNow implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject(new ChuckDuration(vm.getCurrentTime())); }
    }

    public static class PushMe implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject(s); }
    }

    public static class PushMachine implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject(new MachineObject()); }
    }
}
