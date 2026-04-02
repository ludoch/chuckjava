package org.chuck.core.instr;

import org.chuck.core.ChuckArray;

public class ComplexInstrs {
    
    public static class Add extends ArrayBinOpInstr {
        public Add() { super("complex", 2); }
        @Override protected void compute(ChuckArray lhs, ChuckArray rhs, ChuckArray res) {
            res.setFloat(0, lhs.getFloat(0) + rhs.getFloat(0));
            res.setFloat(1, lhs.getFloat(1) + rhs.getFloat(1));
        }
    }

    public static class Sub extends ArrayBinOpInstr {
        public Sub() { super("complex", 2); }
        @Override protected void compute(ChuckArray lhs, ChuckArray rhs, ChuckArray res) {
            res.setFloat(0, lhs.getFloat(0) - rhs.getFloat(0));
            res.setFloat(1, lhs.getFloat(1) - rhs.getFloat(1));
        }
    }

    public static class Mul extends ArrayBinOpInstr {
        public Mul() { super("complex", 2); }
        @Override protected void compute(ChuckArray lhs, ChuckArray rhs, ChuckArray res) {
            double a = lhs.getFloat(0), b = lhs.getFloat(1);
            double c = rhs.getFloat(0), d = rhs.getFloat(1);
            res.setFloat(0, a * c - b * d);
            res.setFloat(1, a * d + b * c);
        }
    }

    public static class Div extends ArrayBinOpInstr {
        public Div() { super("complex", 2); }
        @Override protected void compute(ChuckArray lhs, ChuckArray rhs, ChuckArray res) {
            double a = lhs.getFloat(0), b = lhs.getFloat(1);
            double c = rhs.getFloat(0), d = rhs.getFloat(1);
            double denom = c * c + d * d;
            if (denom < 1e-300) {
                res.setFloat(0, 0.0);
                res.setFloat(1, 0.0);
            } else {
                res.setFloat(0, (a * c + b * d) / denom);
                res.setFloat(1, (b * c - a * d) / denom);
            }
        }
    }
}
