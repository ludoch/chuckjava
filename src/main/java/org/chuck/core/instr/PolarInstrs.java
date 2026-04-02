package org.chuck.core.instr;

import org.chuck.core.ChuckArray;

public class PolarInstrs {
    
    public static class Add extends ArrayBinOpInstr {
        public Add() { super("polar", 2); }
        @Override protected void compute(ChuckArray lhs, ChuckArray rhs, ChuckArray res) {
            double r1 = lhs.getFloat(0), t1 = lhs.getFloat(1);
            double r2 = rhs.getFloat(0), t2 = rhs.getFloat(1);
            double re = r1 * Math.cos(t1) + r2 * Math.cos(t2);
            double im = r1 * Math.sin(t1) + r2 * Math.sin(t2);
            res.setFloat(0, Math.sqrt(re * re + im * im));
            res.setFloat(1, Math.atan2(im, re));
        }
    }

    public static class Sub extends ArrayBinOpInstr {
        public Sub() { super("polar", 2); }
        @Override protected void compute(ChuckArray lhs, ChuckArray rhs, ChuckArray res) {
            double r1 = lhs.getFloat(0), t1 = lhs.getFloat(1);
            double r2 = rhs.getFloat(0), t2 = rhs.getFloat(1);
            double re = r1 * Math.cos(t1) - r2 * Math.cos(t2);
            double im = r1 * Math.sin(t1) - r2 * Math.sin(t2);
            res.setFloat(0, Math.sqrt(re * re + im * im));
            res.setFloat(1, Math.atan2(im, re));
        }
    }

    public static class Mul extends ArrayBinOpInstr {
        public Mul() { super("polar", 2); }
        @Override protected void compute(ChuckArray lhs, ChuckArray rhs, ChuckArray res) {
            res.setFloat(0, lhs.getFloat(0) * rhs.getFloat(0));
            res.setFloat(1, lhs.getFloat(1) + rhs.getFloat(1));
        }
    }

    public static class Div extends ArrayBinOpInstr {
        public Div() { super("polar", 2); }
        @Override protected void compute(ChuckArray lhs, ChuckArray rhs, ChuckArray res) {
            double mag = rhs.getFloat(0);
            res.setFloat(0, mag < 1e-300 ? 0.0 : lhs.getFloat(0) / mag);
            res.setFloat(1, lhs.getFloat(1) - rhs.getFloat(1));
        }
    }
}
