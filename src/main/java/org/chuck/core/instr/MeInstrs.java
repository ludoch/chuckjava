package org.chuck.core.instr;

import org.chuck.core.*;

public class MeInstrs {
    public static class MeDir implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            long level = s.reg.getSp() > 0 ? s.reg.popAsLong() : 0;
            s.reg.pushObject(new ChuckString(s.dir((int) level)));
        }
    }

    public static class MeArgs implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.push((long) s.args());
        }
    }

    public static class MeArg implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            int idx = (int) s.reg.popLong();
            s.reg.pushObject(new ChuckString(s.arg(idx)));
        }
    }

    public static class MeId implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.push((long) s.id());
        }
    }

    public static class MeExit implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            s.abort();
        }
    }
}
