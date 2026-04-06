package org.chuck.core.instr;

import org.chuck.core.*;

public class VarInstrs {
    public static class LoadLocal implements ChuckInstr {
        int offset; public LoadLocal(int o) { offset = o; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            int idx = s.getFramePointer() + offset;
            if (s.mem.isObjectAt(idx)) {
                Object obj = s.mem.getRef(idx);
                s.reg.pushObject(obj);
            } else if (s.mem.isDoubleAt(idx)) {
                double val = Double.longBitsToDouble(s.mem.getData(idx));
                s.reg.push(val);
            } else {
                long val = s.mem.getData(idx);
                s.reg.push(val);
            }
        }
        @Override public String toString() { return "LoadLocal(" + offset + ")"; }
    }

    public static class StoreLocal implements ChuckInstr {
        int offset; public StoreLocal(int o) { offset = o; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            int idx = s.getFramePointer() + offset;
            if (s.reg.isObject(0)) {
                Object obj = s.reg.peekObject(0);
                if (obj instanceof ChuckString cs) {
                    obj = new ChuckString(cs.toString());
                }
                s.mem.setRef(idx, (ChuckObject) obj);
            } else if (s.reg.isDouble(0)) {
                double val = s.reg.peekAsDouble(0);
                s.mem.setData(idx, val);
            } else {
                long val = s.reg.peekLong(0);
                s.mem.setData(idx, val);
            }
            if (idx >= s.mem.getSp()) s.mem.setSp(idx + 1);
        }
        @Override public String toString() { return "StoreLocal(" + offset + ")"; }
    }

    public static class SetGlobalObjectOnly implements ChuckInstr {
        String name; public SetGlobalObjectOnly(String n) { name = n; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            vm.setGlobalObject(name, s.reg.peekObject(0));
        }
    }

    public static class SetGlobalObjectOrInt implements ChuckInstr {
        String name; public SetGlobalObjectOrInt(String n) { name = n; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object val = null;
            if (s.reg.isObject(0)) val = s.reg.peekObject(0);
            if (val != null && val instanceof ChuckString cs) {
                val = new ChuckString(cs.toString());
            }
            if (s.reg.getSp() > 0) {
                if (val != null) {
                    vm.setGlobalObject(name, val);
                } else if (s.reg.isDouble(0)) {
                    double d = s.reg.peekAsDouble(0);
                    vm.setGlobalFloat(name, d);
                } else {
                    long l = s.reg.peekLong(0);
                    vm.setGlobalInt(name, l);
                }
            }
        }
        @Override public String toString() { return "SetGlobal(" + name + ")"; }
    }

    public static class GetGlobalObjectOrInt implements ChuckInstr {
        String name; public GetGlobalObjectOrInt(String n) { name = n; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (vm.isGlobalObject(name)) {
                Object obj = vm.getGlobalObject(name);
                s.reg.pushObject(obj);
            } else if (vm.isGlobalDouble(name)) {
                double val = vm.getGlobalFloat(name);
                s.reg.push(val);
            } else {
                long val = vm.getGlobalInt(name);
                s.reg.push(val);
            }
        }
        @Override public String toString() { return "GetGlobal(" + name + ")"; }
    }

    public static class MoveArgs implements ChuckInstr {
        int a; public MoveArgs(int v) { a = v; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (a <= 0) return;
            Object[] args = new Object[a];
            boolean[] isD = new boolean[a];
            for (int i = a - 1; i >= 0; i--) {
                if (s.reg.getSp() > 0) {
                    isD[i] = s.reg.isDouble(0);
                    if (s.reg.isObject(0)) args[i] = s.reg.popObject();
                    else if (isD[i]) args[i] = s.reg.popAsDouble();
                    else args[i] = s.reg.popAsLong();
                }
            }
            for (int i = 0; i < a; i++) {
                Object arg = args[i];
                if (arg instanceof ChuckObject co) s.mem.pushObject(co);
                else if (isD[i]) s.mem.push((Double) arg);
                else if (arg instanceof Long l) s.mem.push(l);
                else s.mem.pushObject(arg);
            }
        }
    }

    public static class SwapLocal implements ChuckInstr {
        int o1, o2;
        public SwapLocal(int a, int b) { o1 = a; o2 = b; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            int fp = s.getFramePointer();
            int i1 = fp + o1, i2 = fp + o2;
            long d1 = s.mem.getData(i1);
            Object r1 = s.mem.getRef(i1);
            boolean isObj1 = s.mem.isObjectAt(i1);
            boolean isDbl1 = s.mem.isDoubleAt(i1);

            if (s.mem.isObjectAt(i2)) s.mem.setRef(i1, s.mem.getRef(i2));
            else if (s.mem.isDoubleAt(i2)) s.mem.setData(i1, Double.longBitsToDouble(s.mem.getData(i2)));
            else s.mem.setData(i1, s.mem.getData(i2));

            if (isObj1) s.mem.setRef(i2, r1);
            else if (isDbl1) s.mem.setData(i2, Double.longBitsToDouble(d1));
            else s.mem.setData(i2, d1);

            if (s.mem.isObjectAt(i1)) s.reg.pushObject(s.mem.getRef(i1));
            else if (s.mem.isDoubleAt(i1)) s.reg.push(Double.longBitsToDouble(s.mem.getData(i1)));
            else s.reg.push(s.mem.getData(i1));
        }
    }

    public static class StoreLocalOrGlobal implements ChuckInstr {
        String name; Integer offset;
        public StoreLocalOrGlobal(String n, Integer o) { name = n; offset = o; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object val = s.reg.isObject(0) ? s.reg.peekObject(0) : null;
            if (val instanceof ChuckString cs) {
                val = new ChuckString(cs.toString());
            }
            if (offset != null) {
                int idx = s.getFramePointer() + offset;
                if (val != null) s.mem.setRef(idx, (ChuckObject) val);
                else if (s.reg.isDouble(0)) s.mem.setData(idx, s.reg.peekAsDouble(0));
                else s.mem.setData(idx, s.reg.peekLong(0));
                if (idx >= s.mem.getSp()) s.mem.setSp(idx + 1);
            } else {
                if (val != null) vm.setGlobalObject(name, val);
                else if (s.reg.isDouble(0)) vm.setGlobalFloat(name, s.reg.peekAsDouble(0));
                else vm.setGlobalInt(name, s.reg.peekLong(0));
            }
        }
    }
}
