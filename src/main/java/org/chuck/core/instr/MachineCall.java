package org.chuck.core.instr;

import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckInstr;
import org.chuck.core.ChuckShred;
import org.chuck.core.ChuckString;
import org.chuck.core.ChuckType;
import org.chuck.core.ChuckVM;

public class MachineCall implements ChuckInstr {
  String method;
  int argc;

  public MachineCall(String m, int a) {
    method = m;
    argc = a;
  }

  @Override
  public void execute(ChuckVM vm, ChuckShred s) {
    Object[] args = new Object[argc];
    for (int i = argc - 1; i >= 0; i--) {
      if (s.reg.isObject(0)) {
        args[i] = s.reg.popObject();
      } else if (s.reg.isDouble(0)) {
        args[i] = s.reg.popAsDouble();
      } else {
        args[i] = s.reg.popLong();
      }
    }
    switch (method) {
      case "add" -> {
        String path = args.length > 0 ? String.valueOf(args[0]) : "";
        s.reg.push((long) vm.add(path, s));
      }
      case "remove" -> {
        if (args.length > 0 && args[0] != null) {
          long id = ((Number) args[0]).longValue();
          vm.removeShred((int) id);
          s.reg.push(id);
        } else {
          s.reg.push(0L);
        }
      }
      case "replace" -> {
        if (args.length > 1 && args[0] != null) {
          long id = ((Number) args[0]).longValue();
          String path = String.valueOf(args[1]);
          s.reg.push((long) vm.replace((int) id, path, s));
        } else {
          s.reg.push(0L);
        }
      }
      case "status" -> {
        vm.print(vm.status());
        s.reg.push(1L);
      }
      case "clear", "removeAll" -> {
        vm.clear();
        s.reg.push(0L);
      }
      case "eval" -> {
        String src = args.length > 0 ? String.valueOf(args[0]) : "";
        long id = vm.eval(src);
        s.reg.push(id);
        s.yield(0);
      }
      case "numShreds" -> s.reg.push((long) vm.getActiveShredCount());
      case "shredExists" -> {
        if (args.length > 0 && args[0] != null) {
          int sid = ((Number) args[0]).intValue();
          s.reg.push(vm.shredExists(sid) ? 1L : 0L);
        } else {
          s.reg.push(0L);
        }
      }
      case "shreds" -> {
        int[] ids = vm.getActiveShredIds();
        ChuckArray arr = new ChuckArray(ChuckType.ARRAY, ids.length);
        for (int i = 0; i < ids.length; i++) {
          arr.setInt(i, ids[i]);
        }
        s.reg.pushObject(arr);
      }
      case "crash" -> {
        vm.print("[chuck]: (VM) crash! (by request)\n");
        System.exit(1);
      }
      case "resetID" -> {
        vm.resetShredId();
        s.reg.push(0L);
      }
      case "gc" -> {
        vm.gc();
        s.reg.push(0L);
      }
      case "version" -> s.reg.pushObject(new ChuckString(vm.getVersion()));
      case "platform" -> s.reg.pushObject(new ChuckString(vm.getPlatform()));
      case "jitter" -> s.reg.push(vm.getAverageJitter());
      case "maxJitter" -> s.reg.push((double) vm.getMaxJitter());
      case "drift" -> s.reg.push(vm.getAverageDrift());
      case "maxDrift" -> s.reg.push(vm.getMaxDrift());
      case "loglevel" -> {
        if (argc > 0 && args[0] != null) {
          vm.setLogLevel(((Number) args[0]).intValue());
          s.reg.push(0L);
        } else {
          s.reg.push((long) vm.getLogLevel());
        }
      }
      case "setloglevel" -> {
        vm.setLogLevel(args.length > 0 && args[0] != null ? ((Number) args[0]).intValue() : 1);
        s.reg.push(0L);
      }
      case "timeofday" -> s.reg.push(Double.doubleToRawLongBits(vm.getTimeOfDay()));
      // alias: os() same as platform()
      case "os" -> s.reg.pushObject(new ChuckString(vm.getPlatform()));
      // intsize() — pointer/int size in bits on this platform
      case "intsize" -> s.reg.push((long) (Long.SIZE));
      // printStatus() — dump active shreds to console
      case "printStatus" -> {
        vm.status();
        s.reg.push(0L);
      }
      // printTimeCheck() — timing diagnostics (no-op stub; returns 0)
      case "printTimeCheck" -> s.reg.push(0L);
      // removeLastShred() — remove most recently sporked shred
      case "removeLastShred" -> {
        int removed = vm.removeLastShred();
        s.reg.push((long) removed);
      }
      // spork(string) — spork a .ck file by path, like Machine.add()
      case "spork" -> {
        String path = args.length > 0 ? String.valueOf(args[0]) : "";
        s.reg.push((long) vm.add(path));
      }
      // eval(string, args[]) — eval ChucK source with argument array
      case "evalWithArgs" -> {
        String src = args.length > 0 ? String.valueOf(args[0]) : "";
        ChuckArray argArr = args.length > 1 && args[1] instanceof ChuckArray a ? a : null;
        long id = vm.eval(src, argArr);
        s.reg.push(id);
        s.yield(0);
      }
      // operator namespace stack stubs
      case "operatorsPush", "operatorsPop" -> s.reg.push(0L);
      case "operatorsStackLevel" -> s.reg.push(0L);
      // refcount/sp debug stubs
      case "refcount", "sp_reg", "sp_mem" -> s.reg.push(0L);
      // silent()/realtime() already handled in emitter; keep here for dynamic dispatch
      case "silent" -> s.reg.push(1L);
      case "realtime" -> s.reg.push(0L);
      default -> s.reg.push(0L);
    }
  }
}
