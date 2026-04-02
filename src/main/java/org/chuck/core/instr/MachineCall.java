package org.chuck.core.instr;

import org.chuck.core.*;
import java.util.Map;

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
                s.reg.push((long) vm.add(path));
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
                    s.reg.push((long) vm.replace((int) id, path));
                } else {
                    s.reg.push(0L);
                }
            }
            case "status" -> {
                vm.status();
                s.reg.push(0L);
            }
            case "clear", "removeAll" -> {
                vm.clear();
                s.reg.push(0L);
            }
            case "eval" -> {
                String src = args.length > 0 ? String.valueOf(args[0]) : "";
                long id = vm.eval(src);
                s.reg.push(id);
                vm.advanceTime(1);
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
            default -> s.reg.push(0L);
        }
    }
}
