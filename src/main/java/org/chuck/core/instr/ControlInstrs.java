package org.chuck.core.instr;

import org.chuck.core.*;

public class ControlInstrs {
  public static class Jump implements ChuckInstr {
    public int target;

    public Jump(int t) {
      target = t;
    }

    public int getTarget() {
      return target;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      s.setPc(target);
    }
  }

  public static class JumpIfFalse implements ChuckInstr {
    public int target;

    public JumpIfFalse(int t) {
      target = t;
    }

    public int getTarget() {
      return target;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      long val = s.reg.popAsLong();
      if (val == 0) {
        s.setPc(target);
      }
    }
  }

  public static class JumpIfTrue implements ChuckInstr {
    public int target;

    public JumpIfTrue(int t) {
      target = t;
    }

    public int getTarget() {
      return target;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      long val = s.reg.popAsLong();
      if (val != 0) s.setPc(target);
    }
  }

  public static class JumpIfFalseAndPushFalse implements ChuckInstr {
    public int target;

    public JumpIfFalseAndPushFalse(int t) {
      target = t;
    }

    public int getTarget() {
      return target;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      if (s.reg.peekLong(0) == 0) {
        s.setPc(target);
      } else {
        s.reg.pop();
      }
    }
  }

  public static class JumpIfTrueAndPushTrue implements ChuckInstr {
    public int target;

    public JumpIfTrueAndPushTrue(int t) {
      target = t;
    }

    public int getTarget() {
      return target;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      if (s.reg.peekLong(0) != 0) {
        s.setPc(target);
      } else {
        s.reg.pop();
      }
    }
  }

  public static class ReturnFunc implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
      Object self = shred.thisStack.isEmpty() ? null : shred.thisStack.pop();

      int fp = shred.getFramePointer();
      if (fp < 4) {
        shred.abort();
        return;
      }

      int savedRegSp = (int) shred.mem.getData(fp - 1);
      int savedFP = (int) shred.mem.getData(fp - 2);
      int savedPc = (int) shred.mem.getData(fp - 3);
      ChuckCode savedCode = (ChuckCode) shred.mem.getRef(fp - 4);

      // Return value preservation (if any)
      long retPrim = 0L;
      Object retObj = null;
      boolean retIsDouble = false;
      boolean hasReturn = shred.reg.getSp() > savedRegSp;

      if (hasReturn) {
        retIsDouble = shred.reg.isDouble(0);
        if (shred.reg.isObject(0)) retObj = shred.reg.popObject();
        else if (retIsDouble) retPrim = Double.doubleToRawLongBits(shred.reg.popDouble());
        else retPrim = shred.reg.popLong();
      }

      // Restore state
      shred.reg.setSp(savedRegSp);
      shred.setCode(savedCode);
      shred.setPc(savedPc);
      shred.setFramePointer(savedFP);
      shred.mem.setSp(fp - 4);

      // Push return value back onto reg stack
      if (hasReturn) {
        if (retObj != null) {
          shred.reg.pushObject(retObj);
        } else if (retIsDouble) {
          double dv = Double.longBitsToDouble(retPrim);
          shred.reg.push(dv);
        } else {
          shred.reg.push(retPrim);
        }
      }
    }
  }

  public static class ReturnMethod implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
      Object self = shred.thisStack.isEmpty() ? null : shred.thisStack.pop();

      int fp = shred.getFramePointer();
      if (fp < 4) {
        shred.abort();
        return;
      }

      int savedRegSp = (int) shred.mem.getData(fp - 1);
      int savedFP = (int) shred.mem.getData(fp - 2);
      int savedPc = (int) shred.mem.getData(fp - 3);
      ChuckCode savedCode = (ChuckCode) shred.mem.getRef(fp - 4);

      // Return value preservation (if any)
      long retPrim = 0L;
      Object retObj = null;
      boolean retIsDouble = false;
      boolean hasReturn = shred.reg.getSp() > savedRegSp;

      if (hasReturn) {
        retIsDouble = shred.reg.isDouble(0);
        if (shred.reg.isObject(0)) retObj = shred.reg.popObject();
        else if (retIsDouble) retPrim = Double.doubleToRawLongBits(shred.reg.popDouble());
        else retPrim = shred.reg.popLong();
      }

      // Restore state
      shred.reg.setSp(savedRegSp);
      shred.setCode(savedCode);
      shred.setPc(savedPc);
      shred.setFramePointer(savedFP);
      shred.mem.setSp(fp - 4);

      // Push return value back onto reg stack
      if (hasReturn) {
        if (retObj != null) {
          shred.reg.pushObject(retObj);
        } else if (retIsDouble) {
          double dv = Double.longBitsToDouble(retPrim);
          shred.reg.push(dv);
        } else {
          shred.reg.push(retPrim);
        }
      }
    }
  }
}
