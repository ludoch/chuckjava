package org.chuck.core.instr;

import org.chuck.core.*;

public class MathInstrs {
    public static class StdFunc implements ChuckInstr {
        String fn;
        int argc;
        public StdFunc(String f, int a) { fn = f; argc = a; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            int a = argc;
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
                case "getenv" -> {
                    if (argc == 2) {
                        Object def = s.reg.popObject();
                        Object key = s.reg.popObject();
                        String ks = key != null ? key.toString() : null;
                        String ds = def != null ? def.toString() : "";
                        String val = Std.getenv(ks, ds);
                        s.reg.pushObject(new ChuckString(val));
                    } else {
                        Object o = s.reg.popObject();
                        String key = o != null ? o.toString() : "";
                        String val = Std.getenv(key);
                        s.reg.pushObject(new ChuckString(val != null ? val : ""));
                    }
                }
                case "setenv" -> {
                    Object v = s.reg.popObject();
                    Object k = s.reg.popObject();
                    String val = v != null ? v.toString() : "";
                    String key = k != null ? k.toString() : "";
                    Std.setenv(key, val);
                }
                case "range" -> {
                    if (a == 1) {
                        long stop = s.reg.popLong();
                        s.reg.pushObject(Std.range(stop));
                    } else if (a == 2) {
                        long stop = s.reg.popLong();
                        long start = s.reg.popLong();
                        s.reg.pushObject(Std.range(start, stop));
                    } else if (a == 3) {
                        long step = s.reg.popLong();
                        long stop = s.reg.popLong();
                        long start = s.reg.popLong();
                        s.reg.pushObject(Std.range(start, stop, step));
                    } else {
                        s.reg.pushObject(new ChuckArray(ChuckType.ARRAY, 0));
                    }
                }
                default -> {}
            }
        }
    }

    public static class MathFunc implements ChuckInstr {
        String fn;
        public MathFunc(String f) { fn = f; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (fn.equals("rtop")) {
                Object b = s.reg.popObject();
                Object a = s.reg.popObject();
                if (a instanceof ChuckArray aa && b instanceof ChuckArray bb) {
                    if ("complex".equals(aa.vecTag) && "polar".equals(bb.vecTag)) {
                        double re = aa.getFloat(0), im = aa.getFloat(1);
                        bb.setFloat(0, Math.sqrt(re*re + im*im));
                        bb.setFloat(1, Math.atan2(im, re));
                    } else {
                        int len = Math.min(aa.size(), bb.size());
                        for (int i = 0; i < len; i++) {
                            Object elemA = aa.getObject(i), elemB = bb.getObject(i);
                            if (elemA instanceof ChuckArray ca && elemB instanceof ChuckArray cb) {
                                double re = ca.getFloat(0), im = ca.getFloat(1);
                                cb.setFloat(0, Math.sqrt(re * re + im * im));
                                cb.setFloat(1, Math.atan2(im, re));
                            }
                        }
                    }
                }
                s.reg.pushObject(b);
                return;
            }
            if (fn.equals("ptor")) {
                Object b = s.reg.popObject();
                Object a = s.reg.popObject();
                if (a instanceof ChuckArray aa && b instanceof ChuckArray bb) {
                    if ("polar".equals(aa.vecTag) && "complex".equals(bb.vecTag)) {
                        double mag = aa.getFloat(0), ph = aa.getFloat(1);
                        bb.setFloat(0, mag * Math.cos(ph));
                        bb.setFloat(1, mag * Math.sin(ph));
                    } else {
                        int len = Math.min(aa.size(), bb.size());
                        for (int i = 0; i < len; i++) {
                            Object elemA = aa.getObject(i), elemB = bb.getObject(i);
                            if (elemA instanceof ChuckArray ca && elemB instanceof ChuckArray cb) {
                                double mag = ca.getFloat(0), ph = ca.getFloat(1);
                                cb.setFloat(0, mag * Math.cos(ph));
                                cb.setFloat(1, mag * Math.sin(ph));
                            }
                        }
                    }
                }
                s.reg.pushObject(b);
                return;
            }

            // Check for complex/polar objects first
            if (s.reg.isObject(0)) {
                Object obj = s.reg.peekObject(0);
                if (obj instanceof ChuckArray a) {
                    if ("complex".equals(a.vecTag) || "polar".equals(a.vecTag)) {
                        s.reg.popObject(); // remove the first arg
                        double re, im;
                        if ("polar".equals(a.vecTag)) {
                            double mag = a.getFloat(0), phase = a.getFloat(1);
                            re = mag * Math.cos(phase); im = mag * Math.sin(phase);
                        } else {
                            re = a.getFloat(0); im = a.getFloat(1);
                        }

                        // Special cases that return scalar
                        switch (fn) {
                            case "re" -> { s.reg.push(re); return; }
                            case "im" -> { s.reg.push(im); return; }
                            case "mag" -> { s.reg.push(Math.sqrt(re*re + im*im)); return; }
                            case "phase" -> { s.reg.push(Math.atan2(im, re)); return; }
                            case "abs" -> { s.reg.push(Math.sqrt(re*re + im*im)); return; }
                            case "isnan" -> { s.reg.push((Double.isNaN(re) || Double.isNaN(im)) ? 1L : 0L); return; }
                            case "isinf" -> { s.reg.push((Double.isInfinite(re) || Double.isInfinite(im)) ? 1L : 0L); return; }
                        }

                        ChuckArray res = new ChuckArray(ChuckType.ARRAY, 2);
                        res.vecTag = "complex"; // Default complex result

                        switch (fn) {
                            case "sin" -> { res.setFloat(0, Math.sin(re) * Math.cosh(im)); res.setFloat(1, Math.cos(re) * Math.sinh(im)); }
                            case "cos" -> { res.setFloat(0, Math.cos(re) * Math.cosh(im)); res.setFloat(1, -Math.sin(re) * Math.sinh(im)); }
                            case "tan" -> {
                                double den = Math.cos(2*re) + Math.cosh(2*im);
                                res.setFloat(0, Math.sin(2*re) / den);
                                res.setFloat(1, Math.sinh(2*im) / den);
                            }
                            case "asin" -> {
                                double re2 = re*re - im*im, im2 = 2*re*im;
                                double dre = 1 - re2, dim = -im2;
                                double r = Math.sqrt(Math.sqrt(dre*dre + dim*dim)), t = Math.atan2(dim, dre) / 2.0;
                                double sre = r*Math.cos(t), sim = r*Math.sin(t);
                                double lre = -im + sre, lim = re + sim;
                                double lr = Math.sqrt(lre*lre + lim*lim), lt = Math.atan2(lim, lre);
                                res.setFloat(0, lt); res.setFloat(1, -Math.log(lr));
                            }
                            case "acos" -> {
                                double re2 = re*re - im*im, im2 = 2*re*im;
                                double dre = 1 - re2, dim = -im2;
                                double r = Math.sqrt(Math.sqrt(dre*dre + dim*dim)), t = Math.atan2(dim, dre) / 2.0;
                                double sre = r*Math.cos(t), sim = r*Math.sin(t);
                                double lre = -im + sre, lim = re + sim;
                                double lr = Math.sqrt(lre*lre + lim*lim), lt = Math.atan2(lim, lre);
                                res.setFloat(0, Math.PI/2.0 - lt); res.setFloat(1, Math.log(lr));
                            }
                            case "atan" -> {
                                double nre = re, nim = 1 + im, dre = -re, dim = 1 - im;
                                double dmag2 = dre*dre + dim*dim;
                                double qre = (nre*dre + nim*dim)/dmag2, qim = (nim*dre - nre*dim)/dmag2;
                                double qr = Math.sqrt(qre*qre + qim*qim), qt = Math.atan2(qim, qre);
                                res.setFloat(0, -qt/2.0); res.setFloat(1, Math.log(qr)/2.0);
                            }
                            case "atan2" -> {
                                double bre, bim;
                                if (s.reg.isObject(0)) {
                                    Object bObj = s.reg.popObject();
                                    if (bObj instanceof ChuckArray b) {
                                        if ("polar".equals(b.vecTag)) {
                                            double bmag = b.getFloat(0), bph = b.getFloat(1);
                                            bre = bmag * Math.cos(bph); bim = bmag * Math.sin(bph);
                                        } else { bre = b.getFloat(0); bim = b.getFloat(1); }
                                    } else bre = bim = 0;
                                } else { bre = s.reg.popAsDouble(); bim = 0; }
                                res.setFloat(0, Math.atan2(re, bre)); res.setFloat(1, 0.0);
                            }
                            case "pow" -> {
                                double bre, bim;
                                if (s.reg.isObject(0)) {
                                    Object bObj = s.reg.popObject();
                                    if (bObj instanceof ChuckArray b) {
                                        if ("polar".equals(b.vecTag)) {
                                            double bmag = b.getFloat(0), bph = b.getFloat(1);
                                            bre = bmag * Math.cos(bph); bim = bmag * Math.sin(bph);
                                        } else { bre = b.getFloat(0); bim = b.getFloat(1); }
                                    } else bre = bim = 0;
                                } else { bre = s.reg.popAsDouble(); bim = 0; }
                                double lr = Math.log(Math.sqrt(re*re + im*im)), lt = Math.atan2(im, re);
                                double are = bre*lr - bim*lt, aim = bre*lt + bim*lr, expAre = Math.exp(are);
                                res.setFloat(0, expAre * Math.cos(aim)); res.setFloat(1, expAre * Math.sin(aim));
                            }
                            case "sqrt" -> {
                                double r = Math.sqrt(Math.sqrt(re * re + im * im)), theta = Math.atan2(im, re) / 2.0;
                                res.setFloat(0, r * Math.cos(theta)); res.setFloat(1, r * Math.sin(theta));
                            }
                            case "floor" -> { res.setFloat(0, Math.floor(re)); res.setFloat(1, Math.floor(im)); }
                            case "ceil" -> { res.setFloat(0, Math.ceil(re)); res.setFloat(1, Math.ceil(im)); }
                            case "log" -> { res.setFloat(0, Math.log(Math.sqrt(re*re + im*im))); res.setFloat(1, Math.atan2(im, re)); }
                            case "exp" -> { double expRe = Math.exp(re); res.setFloat(0, expRe * Math.cos(im)); res.setFloat(1, expRe * Math.sin(im)); }
                            default -> { res.setFloat(0, 0.0); res.setFloat(1, 0.0); }
                        }
                        
                        // If original was polar, convert result back to polar
                        if ("polar".equals(a.vecTag)) {
                            double rre = res.getFloat(0), rim = res.getFloat(1);
                            res.vecTag = "polar";
                            res.setFloat(0, Math.sqrt(rre*rre + rim*rim));
                            res.setFloat(1, Math.atan2(rim, rre));
                        }
                        s.reg.pushObject(res);
                        return;
                    }
                }
            }

            // Fallback to primitive handling
            switch (fn) {
                case "equal" -> { double b = s.reg.popAsDouble(), a = s.reg.popAsDouble(); s.reg.push(Math.abs(a - b) < 1e-6 ? 1 : 0); }
                case "isinf" -> { s.reg.push(Double.isInfinite(s.reg.popAsDouble()) ? 1L : 0L); }
                case "isnan" -> { s.reg.push(Double.isNaN(s.reg.popAsDouble()) ? 1L : 0L); }
                case "pow" -> { double exp = s.reg.popAsDouble(), base = s.reg.popAsDouble(); s.reg.push(Math.pow(base, exp)); }
                case "atan2" -> { double x = s.reg.popAsDouble(), y = s.reg.popAsDouble(); s.reg.push(Math.atan2(y, x)); }
                case "srandom" -> { long seed = (long) s.reg.popAsDouble(); Std.rng = new java.util.Random(seed); s.reg.push(0.0); }
                case "euclidean" -> {
                    Object b = s.reg.popObject(), a = s.reg.popObject();
                    if (a instanceof ChuckArray aa && b instanceof ChuckArray bb) s.reg.push(aa.euclideanDistance(bb));
                    else s.reg.push(0.0);
                }
                default -> {
                    double v = s.reg.popAsDouble();
                    double res = switch (fn) {
                        case "sin" -> Math.sin(v);
                        case "cos" -> Math.cos(v);
                        case "tan" -> Math.tan(v);
                        case "asin" -> Math.asin(v);
                        case "acos" -> Math.acos(v);
                        case "atan" -> Math.atan(v);
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
                        default -> v;
                    };
                    s.reg.push(res);
                }
            }
        }
    }

    public static class MathRandom implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.push(Math.random()); }
    }

    public static class MathHelp implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {}
    }
}
