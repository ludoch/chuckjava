package org.chuck.core.instr;

import org.chuck.core.*;

public class PushInstrs {
  public static class PushInt implements ChuckInstr {
    long val;

    public PushInt(long v) {
      val = v;
    }

    public long getVal() {
      return val;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.push(val);
    }

    @Override
    public String toString() {
      return "PushInt(" + val + ")";
    }
  }

  public static class PushFloat implements ChuckInstr {
    public double v;

    public PushFloat(double val) {
      v = val;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.push(v);
    }
  }

  public static class PushString implements ChuckInstr {
    String v;

    public PushString(String val) {
      v = val;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.pushObject(s.getString(v));
    }
  }

  public static class PushNow implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.pushObject(s.getDuration(vm.getCurrentTime()));
    }
  }

  public static class PushMe implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.pushObject(s);
    }
  }

  public static class PushMachine implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.pushObject(new MachineObject());
    }
  }

  public static class PushNull implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.pushObject(null);
    }
  }

  public static class PushDac implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.pushObject(vm.dac);
    }
  }

  public static class PushAdc implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.pushObject(vm.adc);
    }
  }

  public static class PushBlackhole implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.pushObject(vm.blackhole);
    }
  }

  public static class PushCherr implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.pushObject(vm.getGlobalObject("cherr"));
    }
  }

  public static class PushChout implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.pushObject(vm.getGlobalObject("chout"));
    }
  }

  public static class PushMaybe implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.push(Math.random() > 0.5 ? 1L : 0L);
    }
  }

  public static class LdcInt implements ChuckInstr {
    public int index;

    public LdcInt(int i) {
      index = i;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.push((Long) s.getCode().getConstant(index));
    }

    @Override
    public String toString() {
      return "LdcInt(" + index + ")";
    }
  }

  public static class LdcFloat implements ChuckInstr {
    int index;

    public LdcFloat(int i) {
      index = i;
    }

    public int getIndex() {
      return index;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.push((Double) s.getCode().getConstant(index));
    }

    @Override
    public String toString() {
      return "LdcFloat(" + index + ")";
    }
  }

  public static class LdcString implements ChuckInstr {
    int index;

    public LdcString(int i) {
      index = i;
    }

    public int getIndex() {
      return index;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.reg.pushObject(s.getString((String) s.getCode().getConstant(index)));
    }

    @Override
    public String toString() {
      return "LdcString(" + index + ")";
    }
  }
}
