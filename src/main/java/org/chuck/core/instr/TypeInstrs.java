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

    public static class CastToComplex implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isObject(0)) {
                Object o = s.reg.popObject();
                if (o instanceof ChuckArray a) {
                    if ("complex".equals(a.vecTag)) { s.reg.pushObject(a); return; }
                    if ("polar".equals(a.vecTag)) {
                        double mag = a.getFloat(0), phase = a.getFloat(1);
                        ChuckArray res = new ChuckArray(ChuckType.ARRAY, 2);
                        res.vecTag = "complex";
                        res.setFloat(0, mag * Math.cos(phase));
                        res.setFloat(1, mag * Math.sin(phase));
                        s.reg.pushObject(res);
                        return;
                    }
                    s.reg.pushObject(a);
                    return;
                }
            }
            double val = s.reg.popAsDouble();
            ChuckArray res = new ChuckArray(ChuckType.ARRAY, 2);
            res.vecTag = "complex";
            res.setFloat(0, val);
            res.setFloat(1, 0.0);
            s.reg.pushObject(res);
        }
    }

    public static class CastToPolar implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isObject(0)) {
                Object o = s.reg.popObject();
                if (o instanceof ChuckArray a) {
                    if ("polar".equals(a.vecTag)) { s.reg.pushObject(a); return; }
                    if ("complex".equals(a.vecTag)) {
                        double re = a.getFloat(0), im = a.getFloat(1);
                        ChuckArray res = new ChuckArray(ChuckType.ARRAY, 2);
                        res.vecTag = "polar";
                        res.setFloat(0, Math.sqrt(re * re + im * im));
                        res.setFloat(1, Math.atan2(im, re));
                        s.reg.pushObject(res);
                        return;
                    }
                    s.reg.pushObject(a);
                    return;
                }
            }
            double val = s.reg.popAsDouble();
            ChuckArray res = new ChuckArray(ChuckType.ARRAY, 2);
            res.vecTag = "polar";
            res.setFloat(0, val);
            res.setFloat(1, 0.0);
            s.reg.pushObject(res);
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
