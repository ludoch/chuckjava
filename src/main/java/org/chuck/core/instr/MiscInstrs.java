package org.chuck.core.instr;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.*;

public class MiscInstrs {
  public static class Yield implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      vm.advanceTime(0);
    }
  }

  public static class ChuckUnchuck implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      Object dest = s.reg.popObject();
      Object src = s.reg.popObject();
      if (src instanceof ChuckUGen su && dest instanceof ChuckUGen du) su.unchuck(du);
      s.reg.pushObject(src);
    }
  }

  public static class CreateDuration implements ChuckInstr {
    String unit;

    public CreateDuration(String u) {
      unit = u;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      double v = s.reg.popAsDouble();
      double smp =
          switch (unit) {
            case "ms" -> v * vm.getSampleRate() / 1000.0;
            case "second" -> v * vm.getSampleRate();
            case "minute" -> v * vm.getSampleRate() * 60.0;
            case "hour" -> v * vm.getSampleRate() * 3600.0;
            case "day" -> v * vm.getSampleRate() * 3600.0 * 24.0;
            case "week" -> v * vm.getSampleRate() * 3600.0 * 24.0 * 7.0;
            case "samp" -> v;
            default -> 0.0;
          };
      s.reg.pushObject(new ChuckDuration(smp));
    }
  }

  public static class ChuckWriteIO implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      Object val = s.reg.pop();
      Object dest = s.reg.popObject();

      String outStr = (val instanceof Double d) ? String.format("%.6f", d) : String.valueOf(val);

      switch (dest) {
        case ChuckIO cio -> cio.write(outStr);
        case FileIO fio -> fio.write(outStr);
        default -> vm.print(outStr);
      }
      s.reg.pushObject(dest);
    }
  }

  public static class RegisterClass implements ChuckInstr {
    String name;
    UserClassDescriptor d;

    public RegisterClass(String n, UserClassDescriptor desc) {
      name = n;
      d = desc;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      boolean initialized = vm.isStaticInitialized(name);
      vm.registerUserClass(name, d);
      // Run static initializer once after the class is registered for the first time in the VM
      if (!initialized
          && d.staticInitCode() != null
          && d.staticInitCode().getNumInstructions() > 0) {
        vm.setStaticInitialized(name);
        vm.executeSynchronous(d.staticInitCode(), s);
      }
    }
  }

  public static class CreateEventConjunction implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      ChuckEvent e2 = (ChuckEvent) s.reg.popObject();
      ChuckEvent e1 = (ChuckEvent) s.reg.popObject();
      ChuckEventConjunction conj = new ChuckEventConjunction();
      conj.addEvent(e1);
      conj.addEvent(e2);
      s.reg.pushObject(conj);
    }
  }

  public static class CreateEventDisjunction implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      ChuckEvent e2 = (ChuckEvent) s.reg.popObject();
      ChuckEvent e1 = (ChuckEvent) s.reg.popObject();
      ChuckEventDisjunction disj = new ChuckEventDisjunction();
      disj.addEvent(e1);
      disj.addEvent(e2);
      s.reg.pushObject(disj);
    }
  }
}
