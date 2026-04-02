package org.chuck.core.instr;

import org.chuck.core.*;

public class ControlInstrs {
    public static class Jump implements ChuckInstr {
        int target; public Jump(int t) { target = t; }
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.setPc(target - 1); }
    }

    public static class JumpIfFalse implements ChuckInstr {
        int target; public JumpIfFalse(int t) { target = t; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.popAsLong() == 0) s.setPc(target - 1);
        }
    }

    public static class JumpIfTrue implements ChuckInstr {
        int target; public JumpIfTrue(int t) { target = t; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.popAsLong() != 0) s.setPc(target - 1);
        }
    }

    public static class JumpIfFalseAndPushFalse implements ChuckInstr {
        int target; public JumpIfFalseAndPushFalse(int t) { target = t; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            long val;
            if (s.reg.isObject(0)) {
                Object o = s.reg.peekObject(0);
                val = switch (o) {
                    case null -> 0L;
                    case ChuckDuration cd -> (long) cd.samples();
                    case Number n -> n.longValue();
                    default -> 1L;
                };
            } else {
                val = s.reg.peekLong(0);
            }
            
            if (val == 0) s.setPc(target - 1);
            else s.reg.pop();
        }
    }

    public static class JumpIfTrueAndPushTrue implements ChuckInstr {
        int target; public JumpIfTrueAndPushTrue(int t) { target = t; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            long val;
            if (s.reg.isObject(0)) {
                Object o = s.reg.peekObject(0);
                val = switch (o) {
                    case null -> 0L;
                    case ChuckDuration cd -> (long) cd.samples();
                    case Number n -> n.longValue();
                    default -> 1L;
                };
            } else {
                val = s.reg.peekLong(0);
            }

            if (val != 0) s.setPc(target - 1);
            else s.reg.pop();
        }
    }
}
