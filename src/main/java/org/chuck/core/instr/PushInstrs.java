package org.chuck.core.instr;

import org.chuck.core.*;

public class PushInstrs {
    public static class PushInt implements ChuckInstr {
        long val; public PushInt(long v) { val = v; }
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.push(val); }
        @Override public String toString() { return "PushInt(" + val + ")"; }
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

    public static class PushNull implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject(null); }
    }

    public static class PushDac implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject(vm.dac); }
    }

    public static class PushAdc implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject(vm.adc); }
    }

    public static class PushBlackhole implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject(vm.blackhole); }
    }

    public static class PushCherr implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject(vm.getGlobalObject("cherr")); }
    }

    public static class PushChout implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject(vm.getGlobalObject("chout")); }
    }

    public static class PushMaybe implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.push(Math.random() > 0.5 ? 1L : 0L); }
    }
}
