package org.chuck.core.instr;

import org.chuck.core.*;
import java.util.Map;

public class VarInstrs {
    public static class LoadLocal implements ChuckInstr {
        int offset; public LoadLocal(int o) { offset = o; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            int idx = s.getFramePointer() + offset;
            if (s.mem.isObjectAt(idx)) s.reg.pushObject(s.mem.getRef(idx));
            else if (s.mem.isDoubleAt(idx)) s.reg.push(Double.longBitsToDouble(s.mem.getData(idx)));
            else s.reg.push(s.mem.getData(idx));
        }
    }

    public static class StoreLocal implements ChuckInstr {
        int offset; public StoreLocal(int o) { offset = o; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            int idx = s.getFramePointer() + offset;
            if (s.reg.isObject(0)) s.mem.setRef(idx, (ChuckObject) s.reg.peekObject(0));
            else if (s.reg.isDouble(0)) s.mem.setData(idx, s.reg.peekAsDouble(0));
            else s.mem.setData(idx, s.reg.peekLong(0));
        }
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
            if (s.reg.isObject(0)) vm.setGlobalObject(name, s.reg.peekObject(0));
            else vm.setGlobalInt(name, s.reg.peekLong(0));
        }
    }

    public static class GetGlobalObjectOrInt implements ChuckInstr {
        String name; public GetGlobalObjectOrInt(String n) { name = n; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object obj = vm.getGlobalObject(name);
            if (obj != null) s.reg.pushObject(obj);
            else s.reg.push(vm.getGlobalInt(name));
        }
    }

    public static class InstantiateSetAndPushLocal implements ChuckInstr {
        String type; int offset, argc; boolean isRef, isStatic; Map<String, UserClassDescriptor> rm;
        public InstantiateSetAndPushLocal(String t, int o, int a, boolean ref, boolean s, Map<String, UserClassDescriptor> reg) {
            type = t; offset = o; argc = a; isRef = ref; isStatic = s; rm = reg;
        }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object[] args = new Object[argc];
            for (int i = argc - 1; i >= 0; i--) args[i] = s.reg.pop();
            ChuckObject obj = null;
            if (!isRef) {
                // Simplified instantiation logic for refactor
                // In practice this calls back into ChuckEmitter.instantiateType
                // We'll move that logic to a common place if needed
            }
            int idx = s.getFramePointer() + offset;
            s.mem.setRef(idx, obj);
            s.reg.pushObject(obj);
        }
    }
}
