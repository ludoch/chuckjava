package org.chuck.core.instr;

import org.chuck.core.*;
import org.chuck.audio.ChuckUGen;

public class UgenInstrs {
    public static class ConnectToDac implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object src = s.reg.peekObject(0);
            if (src instanceof ChuckUGen ugen) ugen.chuckTo(vm.getMultiChannelDac());
        }
    }

    public static class ConnectToBlackhole implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object src = s.reg.peekObject(0);
            if (src instanceof ChuckUGen ugen) ugen.chuckTo(vm.blackhole);
        }
    }

    public static class ConnectToAdc implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object src = s.reg.peekObject(0);
            if (src instanceof ChuckUGen ugen) ugen.chuckTo(vm.adc);
        }
    }

    public static class GetLastOut implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object src = s.reg.popObject();
            if (src instanceof ChuckUGen ugen) s.reg.push((double) ugen.getLastOut());
            else s.reg.push(0.0);
        }
    }

    public static class NewUGen implements ChuckInstr {
        String type; public NewUGen(String t) { type = t; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.pushObject(org.chuck.core.ChuckFactory.instantiateType(type, 0, null, vm.getSampleRate(), vm, s, null));
        }
    }
}
