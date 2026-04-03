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

    public static class ReturnMethod implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred shred) {
            shred.thisStack.pop();
            
            int fp = shred.getFramePointer();
            if (fp < 4) { shred.abort(); return; }

            int savedRegSp = (int) shred.mem.getData(fp - 1);
            int savedFP    = (int) shred.mem.getData(fp - 2);
            int savedPc    = (int) shred.mem.getData(fp - 3);
            ChuckCode savedCode = (ChuckCode) shred.mem.getRef(fp - 4);

            // Return value preservation (if any)
            long retPrim = 0L;
            Object retObj = null;
            boolean retIsDouble = false;
            boolean hasReturn = shred.reg.getSp() > savedRegSp;
            
            if (hasReturn) {
                retIsDouble = shred.reg.isDouble(0);
                if (shred.reg.isObject(0)) retObj = shred.reg.popObject();
                else if (retIsDouble) retPrim = Double.doubleToRawLongBits(shred.reg.popDouble());
                else retPrim = shred.reg.popLong();
            }

            // Restore state
            shred.reg.setSp(savedRegSp);
            shred.setCode(savedCode);
            shred.setPc(savedPc);
            shred.setFramePointer(savedFP);
            shred.mem.setSp(fp - 4);

            // Push return value back onto reg stack
            if (hasReturn) {
                if (retObj != null) shred.reg.pushObject(retObj);
                else if (retIsDouble) shred.reg.push(Double.longBitsToDouble(retPrim));
                else shred.reg.push(retPrim);
            }
        }
    }
}
