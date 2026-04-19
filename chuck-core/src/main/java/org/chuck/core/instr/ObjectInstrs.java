package org.chuck.core.instr;

import java.util.Map;
import org.chuck.core.*;

public class ObjectInstrs {
  // ... (reverting to known stable implementation of global instantiation)
  public static class InstantiateSetAndPushGlobal implements ChuckInstr {
    String n, t, ctorKey;
    int arraySizeCount, ctorArgCount;
    Map<String, UserClassDescriptor> rm;

    public InstantiateSetAndPushGlobal(
        String name,
        String type,
        int arraySzCount,
        int ctorArgCnt,
        String ctorKey,
        Map<String, UserClassDescriptor> m) {
      n = name;
      t = type;
      arraySizeCount = arraySzCount;
      ctorArgCount = ctorArgCnt;
      this.ctorKey = ctorKey;
      rm = m;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      // Pop ctor args
      Object[] ctorArgs = new Object[ctorArgCount];
      boolean[] isDouble = new boolean[ctorArgCount];
      for (int i = ctorArgCount - 1; i >= 0; i--) {
        if (s.reg.getSp() > 0) {
          isDouble[i] = s.reg.isDouble(0);
          if (s.reg.isObject(0)) ctorArgs[i] = s.reg.popObject();
          else if (isDouble[i]) ctorArgs[i] = s.reg.popAsDouble();
          else ctorArgs[i] = s.reg.popAsLong();
        }
      }

      // If it's a primitive global that already exists, don't re-instantiate as null
      boolean r = ChuckLanguage.isPrimitive(t) || ChuckLanguage.isUGen(t);

      if (r) {
        if (!vm.isGlobalObject(n)) {
          vm.setGlobalObject(n, null);
        }
        s.reg.pushObject(vm.getGlobalObject(n));
        return;
      }

      // For objects/arrays: instantiate and set
      ChuckObject obj = ChuckFactory.instantiateType(t, 0, null, vm.getSampleRate(), vm, s, rm);
      if (obj instanceof UserObject uo) {
        UserClassDescriptor desc =
            (rm != null && rm.containsKey(t)) ? rm.get(t) : vm.getUserClass(t);
        if (desc != null) {
          ChuckCode ctorCode = desc.methods().get(ctorKey);
          if (ctorCode != null) {
            s.executeCtorSynchronous(vm, ctorCode, uo, ctorArgs, isDouble);
          }
        }
      }
      vm.setGlobalObject(n, obj);
      s.reg.pushObject(obj);
    }
  }

  public static class InstantiateArrayWithCtorGlobal implements ChuckInstr {
    String t, n, ctorKey;
    int arraySizeCount, ctorArgCount;
    Map<String, UserClassDescriptor> rm;

    public InstantiateArrayWithCtorGlobal(
        String type,
        String name,
        int arraySzCount,
        int ctorArgCnt,
        String ctorKey,
        Map<String, UserClassDescriptor> m) {
      t = type;
      n = name;
      arraySizeCount = arraySzCount;
      ctorArgCount = ctorArgCnt;
      this.ctorKey = ctorKey;
      rm = m;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      Object[] ctorArgs = new Object[ctorArgCount];
      boolean[] isDouble = new boolean[ctorArgCount];
      for (int i = ctorArgCount - 1; i >= 0; i--) {
        if (s.reg.getSp() > 0) {
          isDouble[i] = s.reg.isDouble(0);
          if (s.reg.isObject(0)) ctorArgs[i] = s.reg.popObject();
          else if (isDouble[i]) ctorArgs[i] = s.reg.popAsDouble();
          else ctorArgs[i] = s.reg.popAsLong();
        }
      }
      int sz = 0;
      if (arraySizeCount >= 1 && s.reg.getSp() > 0) {
        sz = (int) s.reg.popAsLong();
      }
      if (sz < 0) sz = 0;

      ChuckArray arr = new ChuckArray(t, sz);
      vm.setGlobalObject(n, arr);
      s.reg.pushObject(arr);
    }
  }
}
