package org.chuck.core.instr;

import org.chuck.core.*;

public class ArithmeticInstrs {
    
    public static class AddAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() < 2) return;
            if (s.reg.isObject(0) || s.reg.isObject(1)) {
                Object r = s.reg.pop(), l = s.reg.pop();
                if (l instanceof ChuckDuration ld && r instanceof ChuckDuration rd) {
                    s.reg.pushObject(new ChuckDuration(ld.samples() + rd.samples()));
                } else {
                    String ls = (l instanceof Double d) ? String.format("%.6f", d) : String.valueOf(l);
                    String rs = (r instanceof Double d) ? String.format("%.6f", d) : String.valueOf(r);
                    s.reg.pushObject(new ChuckString(ls + rs));
                }
            } else if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
                double r = s.reg.popAsDouble(), l = s.reg.popAsDouble(); 
                s.reg.push(l + r);
            } else {
                long r = s.reg.popLong(), l = s.reg.popLong(); 
                s.reg.push(l + r);
            }
        }
    }

    public static class MinusAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() < 2) return;
            if (s.reg.isObject(0) || s.reg.isObject(1)) {
                Object r = s.reg.pop(), l = s.reg.pop();
                if (l instanceof ChuckDuration ld && r instanceof ChuckDuration rd) {
                    s.reg.pushObject(new ChuckDuration(ld.samples() - rd.samples()));
                } else {
                    s.reg.push(0.0);
                }
                return;
            }
            if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
                double r = s.reg.popAsDouble(), l = s.reg.popAsDouble(); s.reg.push(l - r);
            } else {
                long r = s.reg.popAsLong(), l = s.reg.popAsLong(); s.reg.push(l - r);
            }
        }
    }

    public static class TimesAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() < 2) return;
            double r = s.reg.popAsDouble(), l = s.reg.popAsDouble(); s.reg.push(l * r);
        }
    }

    public static class DivideAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() < 2) return;
            double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
            if (Math.abs(r) < 1e-300) s.reg.push(0.0);
            else s.reg.push(l / r);
        }
    }

    public static class ModuloAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() < 2) return;
            if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
                double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
                if (Math.abs(r) < 1e-300) s.reg.push(0.0);
                else s.reg.push(l % r);
            } else {
                long r = s.reg.popLong(), l = s.reg.popLong();
                if (r == 0) s.reg.push(0L);
                else s.reg.push(l % r);
            }
        }
    }

    public static class NegateAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() < 1) return;
            if (s.reg.isObject(0)) {
                Object o = s.reg.pop();
                if (o instanceof ChuckDuration cd) s.reg.pushObject(new ChuckDuration(-cd.samples()));
                else s.reg.push(0.0);
            } else if (s.reg.isDouble(0)) {
                s.reg.push(-s.reg.popAsDouble());
            } else {
                s.reg.push(-s.reg.popLong());
            }
        }
    }

    public static class BitwiseAndAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            long r = s.reg.popAsLong(), l = s.reg.popAsLong();
            s.reg.push(l & r);
        }
    }

    public static class BitwiseOrAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            long r = s.reg.popAsLong(), l = s.reg.popAsLong();
            s.reg.push(l | r);
        }
    }
}
