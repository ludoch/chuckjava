package org.chuck.core.instr;

import org.chuck.core.*;

public class ArrayInstrs {
  public static class GetArrayInt implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      // Index may be a string key (associative) or an integer
      if (s.reg.isObject(0)) {
        Object keyObj = s.reg.popObject();
        Object raw = s.reg.popObject();
        String key =
            keyObj instanceof org.chuck.core.ChuckString cs
                ? cs.toString()
                : String.valueOf(keyObj);
        if (raw instanceof ChuckArray a) {
          Object assocVal = a.getAssocObject(key);
          if (assocVal != null) s.reg.pushObject(assocVal);
          else if (a.isInMap(key) == 1L) {
            double fv = a.getAssocFloat(key);
            s.reg.push(fv);
          } else s.reg.push(0L);
        } else s.reg.push(0L);
      } else {
        long idx = s.reg.popLong();
        Object raw = s.reg.popObject();
        if (raw instanceof ChuckArray a) {
          int i = a.resolveIndex(idx);
          if (a.isObjectAt(i)) s.reg.pushObject(a.getObject(i));
          else if (a.isDoubleAt(i)) s.reg.push(a.getFloat(i));
          else s.reg.push(a.getInt(i));
        } else {
          s.reg.push(0L);
        }
      }
    }
  }

  public static class SetArrayInt implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      // Index may be a string key (associative) or an integer
      if (s.reg.isObject(0)) {
        Object keyObj = s.reg.popObject();
        Object raw = s.reg.popObject();
        if (!(raw instanceof ChuckArray a)) return;
        String key =
            keyObj instanceof org.chuck.core.ChuckString cs
                ? cs.toString()
                : String.valueOf(keyObj);
        if (s.reg.isObject(0)) {
          Object val = s.reg.popObject();
          a.setAssocObject(key, val);
          s.reg.pushObject(val);
        } else if (s.reg.isDouble(0)) {
          double val = s.reg.popAsDouble();
          a.setAssocFloat(key, val);
          s.reg.push(val);
        } else {
          long val = s.reg.popLong();
          a.setAssocInt(key, val);
          s.reg.push(val);
        }
      } else {
        long idx = s.reg.popLong();
        Object raw = s.reg.popObject();
        if (!(raw instanceof ChuckArray a)) {
          return; // Value stays on stack
        }
        int i = a.resolveIndex(idx);
        if (s.reg.isObject(0)) {
          Object val = s.reg.popObject();
          a.setObject(i, val);
          s.reg.pushObject(val);
        } else if (s.reg.isDouble(0)) {
          double val = s.reg.popAsDouble();
          a.setFloat(i, val);
          s.reg.push(val);
        } else {
          long val = s.reg.popLong();
          a.setInt(i, val);
          s.reg.push(val);
        }
      }
    }
  }

  public static class ArrayZero implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      Object o = s.reg.peekObject(0);
      if (o instanceof ChuckArray a) a.zero();
    }
  }

  public static class ShiftLeftOrAppend implements ChuckInstr {
    public ShiftLeftOrAppend() {}

    public ShiftLeftOrAppend(String type) {} // Ignore for now

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      Object rhs = s.reg.pop();
      Object lhs = s.reg.popObject();
      if (lhs instanceof ChuckArray a) {
        a.append(rhs);
      } else if (lhs instanceof ChuckIO io) {
        io.write(rhs);
      }
      s.reg.pushObject(lhs);
    }
  }

  public static class NewArray implements ChuckInstr {
    String t;
    int dims;

    public NewArray(String type, int d) {
      t = type;
      dims = d;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      long[] sizes = new long[dims];
      for (int i = dims - 1; i >= 0; i--) sizes[i] = s.reg.popLong();
      s.reg.pushObject(new ChuckArray(t, (int) sizes[0]));
    }
  }

  public static class NewArrayFromStack implements ChuckInstr {
    int count;
    String vt;

    public NewArrayFromStack(int c) {
      this(c, null);
    }

    public NewArrayFromStack(int c, String v) {
      count = c;
      vt = v;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      ChuckArray a = new ChuckArray(vt, count);
      for (int i = count - 1; i >= 0; i--) {
        if (s.reg.isObject(0)) a.setObject(i, s.reg.popObject());
        else if (s.reg.isDouble(0)) a.setFloat(i, s.reg.popAsDouble());
        else a.setInt(i, s.reg.popLong());
      }
      s.reg.pushObject(a);
    }
  }

  // --- Specialized Array Access ---

  public static class GetArrayIntFast implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      long idx = s.reg.popLong();
      ChuckArray a = (ChuckArray) s.reg.popObject();
      s.reg.push(a.getInt(a.resolveIndex(idx)));
    }
  }

  public static class GetArrayFloatFast implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      long idx = s.reg.popLong();
      ChuckArray a = (ChuckArray) s.reg.popObject();
      s.reg.push(a.getFloat(a.resolveIndex(idx)));
    }
  }

  public static class SetArrayIntFast implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      long idx = s.reg.popLong();
      ChuckArray a = (ChuckArray) s.reg.popObject();
      long val = s.reg.peekLong(0);
      a.setInt(a.resolveIndex(idx), val);
    }
  }

  public static class SetArrayFloatFast implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      long idx = s.reg.popLong();
      ChuckArray a = (ChuckArray) s.reg.popObject();
      double val = s.reg.peekAsDouble(0);
      a.setFloat(a.resolveIndex(idx), val);
    }
  }

  public static class CopyArray implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      Object rhs = s.reg.popObject();
      Object lhs = s.reg.peekObject(0);
      if (lhs instanceof ChuckArray al && rhs instanceof ChuckArray ar) {
        al.copyFrom(ar);
      }
    }
  }
}
