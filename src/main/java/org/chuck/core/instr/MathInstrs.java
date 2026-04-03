package org.chuck.core.instr;

import org.chuck.core.*;

public class MathInstrs {
    public static class StdFunc implements ChuckInstr {
        String fn;
        public StdFunc(String f, int a) { fn = f; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            switch (fn) {
                case "mtof" -> s.reg.push(Std.mtof(s.reg.popAsDouble()));
                case "ftom" -> s.reg.push(Std.ftom(s.reg.popAsDouble()));
                case "powtodb" -> s.reg.push(Std.powtodb(s.reg.popAsDouble()));
                case "rmstodb" -> s.reg.push(Std.rmstodb(s.reg.popAsDouble()));
                case "dbtopow" -> s.reg.push(Std.dbtopow(s.reg.popAsDouble()));
                case "dbtorms" -> s.reg.push(Std.dbtorms(s.reg.popAsDouble()));
                case "dbtolin" -> s.reg.push(Std.dbtolin(s.reg.popAsDouble()));
                case "lintodb" -> s.reg.push(Std.lintodb(s.reg.popAsDouble()));
                case "abs" -> s.reg.push(Std.abs(s.reg.popLong()));
                case "fabs" -> s.reg.push(Std.fabs(s.reg.popAsDouble()));
                case "sgn" -> s.reg.push(Std.sgn(s.reg.popAsDouble()));
                case "rand2" -> { long hi = s.reg.popLong(); long lo = s.reg.popLong(); s.reg.push(Std.rand2(lo, hi)); }
                case "rand2f" -> { double hi = s.reg.popAsDouble(); double lo = s.reg.popAsDouble(); s.reg.push(Std.rand2f(lo, hi)); }
                case "atoi" -> s.reg.push(Std.atoi(s.reg.popObject().toString()));
                case "atof" -> s.reg.push(Std.atof(s.reg.popObject().toString()));
                case "itoa" -> s.reg.pushObject(new ChuckString(Std.itoa(s.reg.popLong())));
                case "ftoi" -> s.reg.push(Std.ftoi(s.reg.popAsDouble()));
                case "systemTime" -> s.reg.push(Std.systemTime());
                default -> {}
            }
        }
    }

    public static class MathFunc implements ChuckInstr {
        String fn;
        public MathFunc(String f) { fn = f; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            switch (fn) {
                case "equal" -> { double b = s.reg.popAsDouble(), a = s.reg.popAsDouble(); s.reg.push(Math.abs(a - b) < 1e-6 ? 1 : 0); return; }
                case "isinf" -> { s.reg.push(Double.isInfinite(s.reg.popAsDouble()) ? 1L : 0L); return; }
                case "isnan" -> { s.reg.push(Double.isNaN(s.reg.popAsDouble()) ? 1L : 0L); return; }
                case "pow" -> { double exp = s.reg.popAsDouble(), base = s.reg.popAsDouble(); s.reg.push(Math.pow(base, exp)); return; }
                case "srandom" -> { long seed = (long) s.reg.popAsDouble(); Std.rng = new java.util.Random(seed); s.reg.push(0.0); return; }
                case "euclidean" -> {
                    Object b = s.reg.popObject();
                    Object a = s.reg.popObject();
                    if (a instanceof ChuckArray aa && b instanceof ChuckArray bb) {
                        s.reg.push(aa.euclideanDistance(bb));
                    } else {
                        s.reg.push(0.0);
                    }
                    return;
                }
                case "rtop" -> {
                    Object b = s.reg.popObject();
                    Object a = s.reg.popObject();
                    if (a instanceof ChuckArray aa && b instanceof ChuckArray bb) {
                        int len = Math.min(aa.size(), bb.size());
                        for (int i = 0; i < len; i++) {
                            Object elemA = aa.getObject(i);
                            Object elemB = bb.getObject(i);
                            if (elemA instanceof ChuckArray ca && elemB instanceof ChuckArray cb) {
                                double re = ca.getFloat(0), im = ca.getFloat(1);
                                cb.setFloat(0, Math.sqrt(re * re + im * im));
                                cb.setFloat(1, Math.atan2(im, re));
                            } else if (aa.vecTag != null && aa.vecTag.equals("complex") && bb.vecTag != null && bb.vecTag.equals("polar")) {
                                double re = aa.getFloat(0), im = aa.getFloat(1);
                                bb.setFloat(0, Math.sqrt(re * re + im * im));
                                bb.setFloat(1, Math.atan2(im, re));
                                break;
                            }
                        }
                    }
                    s.reg.pushObject(b);
                    return;
                }
                case "ptor" -> {
                    Object b = s.reg.popObject();
                    Object a = s.reg.popObject();
                    if (a instanceof ChuckArray aa && b instanceof ChuckArray bb) {
                        int len = Math.min(aa.size(), bb.size());
                        for (int i = 0; i < len; i++) {
                            Object elemA = aa.getObject(i);
                            Object elemB = bb.getObject(i);
                            if (elemA instanceof ChuckArray ca && elemB instanceof ChuckArray cb) {
                                double mag = ca.getFloat(0), phase = ca.getFloat(1);
                                cb.setFloat(0, mag * Math.cos(phase));
                                cb.setFloat(1, mag * Math.sin(phase));
                            } else if (aa.vecTag != null && aa.vecTag.equals("polar") && bb.vecTag != null && bb.vecTag.equals("complex")) {
                                double mag = aa.getFloat(0), phase = aa.getFloat(1);
                                bb.setFloat(0, mag * Math.cos(phase));
                                bb.setFloat(1, mag * Math.sin(phase));
                                break;
                            }
                        }
                    }
                    s.reg.pushObject(b);
                    return;
                }
                default -> {}
            }
            double v = s.reg.popAsDouble();
            double res = switch (fn) {
                case "sin" -> Math.sin(v);
                case "cos" -> Math.cos(v);
                case "sqrt" -> Math.sqrt(v);
                case "abs" -> Math.abs(v);
                case "floor" -> Math.floor(v);
                case "ceil" -> Math.ceil(v);
                case "log" -> Math.log(v);
                case "log2" -> Math.log(v) / Math.log(2);
                case "log10" -> Math.log10(v);
                case "exp" -> Math.exp(v);
                case "round" -> (double) Math.round(v);
                case "trunc" -> (double) (long) v;
                case "tan" -> Math.tan(v);
                case "asin" -> Math.asin(v);
                case "acos" -> Math.acos(v);
                case "atan" -> Math.atan(v);
                default -> v;
            };
            s.reg.push(res);
        }
    }

    public static class MathRandom implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.push(Math.random()); }
    }

    public static class MathHelp implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {}
    }
}
