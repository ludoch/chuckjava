package org.chuck.core.instr;

import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckType;
import org.chuck.core.ChuckVM;
import org.chuck.core.ChuckShred;
import org.chuck.core.ChuckInstr;

public class VecInstrs {
    
    public static class Add extends ArrayBinOpInstr {
        public Add() { super(null, 2); }
        @Override protected void compute(ChuckArray lhs, ChuckArray rhs, ChuckArray res) {
            int len = Math.min(lhs.size(), rhs.size());
            for (int i = 0; i < len; i++) {
                res.setFloat(i, lhs.getFloat(i) + rhs.getFloat(i));
            }
        }
    }

    public static class Sub extends ArrayBinOpInstr {
        public Sub() { super(null, 2); }
        @Override protected void compute(ChuckArray lhs, ChuckArray rhs, ChuckArray res) {
            int len = Math.min(lhs.size(), rhs.size());
            for (int i = 0; i < len; i++) {
                res.setFloat(i, lhs.getFloat(i) - rhs.getFloat(i));
            }
        }
    }

    public static class VecScale implements ChuckInstr {
        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            double scalar = s.reg.popAsDouble();
            Object rawVec = s.reg.popObject();
            if (!(rawVec instanceof ChuckArray vec)) {
                s.reg.pushObject(null);
                return;
            }
            ChuckArray result = new ChuckArray(ChuckType.ARRAY, vec.size());
            result.vecTag = vec.vecTag;
            if ("polar".equals(vec.vecTag)) {
                result.setFloat(0, vec.getFloat(0) * scalar);
                result.setFloat(1, vec.getFloat(1));
            } else {
                for (int i = 0; i < vec.size(); i++) {
                    result.setFloat(i, vec.getFloat(i) * scalar);
                }
            }
            s.reg.pushObject(result);
        }
    }

    public static class Dot implements ChuckInstr {
        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            Object rObj = s.reg.popObject();
            Object lObj = s.reg.popObject();
            if (!(rObj instanceof ChuckArray rhs) || !(lObj instanceof ChuckArray lhs)) {
                s.reg.push(0.0);
                return;
            }
            double dot = lhs.dot(rhs);
            s.reg.push(dot);
        }
    }

    public static class Cross implements ChuckInstr {
        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            Object rObj = s.reg.popObject();
            Object lObj = s.reg.popObject();
            ChuckArray rhs = (rObj instanceof ChuckArray ra) ? ra : new ChuckArray(ChuckType.ARRAY, 3);
            ChuckArray lhs = (lObj instanceof ChuckArray la) ? la : new ChuckArray(ChuckType.ARRAY, 3);
            ChuckArray res = lhs.cross(rhs);
            s.reg.pushObject(res);
        }
    }
}
