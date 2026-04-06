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

    public static class TypeofInstr implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object val = s.reg.isObject(0) ? s.reg.popObject() : s.reg.pop();
            String typeName;
            if (val instanceof UserObject uo) typeName = uo.getTypeName();
            else if (val instanceof org.chuck.audio.ChuckUGen u) typeName = u.getClass().getSimpleName();
            else if (val instanceof ChuckArray a) {
                if (a.vecTag != null) typeName = a.vecTag;
                else typeName = "array";
            }
            else if (val instanceof ChuckString) typeName = "string";
            else if (val instanceof Double) typeName = "float";
            else typeName = "int";
            s.reg.pushObject(new ChuckString(typeName));
        }
    }

    public static class InstanceofInstr implements ChuckInstr {
        private final String typeName;
        public InstanceofInstr(String typeName) { this.typeName = typeName; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object val = s.reg.isObject(0) ? s.reg.popObject() : s.reg.pop();
            boolean result;
            if (val instanceof UserObject uo) result = typeName.equals(uo.getTypeName());
            else if (val instanceof org.chuck.audio.ChuckUGen u) {
                // check class name or superclass
                Class<?> cls = u.getClass();
                result = false;
                while (cls != null) {
                    if (cls.getSimpleName().equals(typeName)) { result = true; break; }
                    cls = cls.getSuperclass();
                }
            }
            else result = switch (typeName) {
                case "int" -> val instanceof Long;
                case "float" -> val instanceof Double;
                case "string" -> val instanceof ChuckString;
                case "array" -> val instanceof ChuckArray;
                default -> false;
            };
            s.reg.push(result ? 1L : 0L);
        }
    }
}
