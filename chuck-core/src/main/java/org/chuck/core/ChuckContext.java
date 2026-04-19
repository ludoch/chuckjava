package org.chuck.core;

/** Context for shred execution, providing bit-transparent memory and register stacks. */
public class ChuckContext {
  public static class Memory {
    private final long[] data = new long[8192];
    private final Object[] refs = new Object[8192];
    private final boolean[] isDouble = new boolean[8192];
    private final boolean[] isObject = new boolean[8192];
    private int sp = 0;
    private int fp = 0;

    public void push(long val) {
      data[sp] = val;
      isDouble[sp] = false;
      isObject[sp] = false;
      refs[sp] = null;
      sp++;
    }

    public void push(double val) {
      data[sp] = Double.doubleToRawLongBits(val);
      isDouble[sp] = true;
      isObject[sp] = false;
      refs[sp] = null;
      sp++;
    }

    public void pushObject(Object obj) {
      refs[sp] = obj;
      isObject[sp] = true;
      isDouble[sp] = false;
      sp++;
    }

    public void setData(int idx, long val) {
      if (idx >= 0 && idx < 8192) {
        data[idx] = val;
        isDouble[idx] = false;
        isObject[idx] = false;
        refs[idx] = null;
      }
    }

    public void setData(int idx, double val) {
      if (idx >= 0 && idx < 8192) {
        data[idx] = Double.doubleToRawLongBits(val);
        isDouble[idx] = true;
        isObject[idx] = false;
        refs[idx] = null;
      }
    }

    public long getData(int idx) {
      return (idx >= 0 && idx < 8192) ? data[idx] : 0L;
    }

    public void setRef(int idx, Object obj) {
      if (idx >= 0 && idx < 8192) {
        refs[idx] = obj;
        isObject[idx] = true;
        isDouble[idx] = false;
      }
    }

    public Object getRef(int idx) {
      return (idx >= 0 && idx < 8192) ? refs[idx] : null;
    }

    public void setIsDouble(int idx, boolean b) {
      if (idx >= 0 && idx < 8192) isDouble[idx] = b;
    }

    public boolean isDoubleAt(int idx) {
      return (idx >= 0 && idx < 8192) && isDouble[idx];
    }

    public void setIsObject(int idx, boolean b) {
      if (idx >= 0 && idx < 8192) isObject[idx] = b;
    }

    public boolean isObjectAt(int idx) {
      return (idx >= 0 && idx < 8192) && isObject[idx];
    }

    public int getSp() {
      return sp;
    }

    public void setSp(int sp) {
      this.sp = sp;
    }

    public int getFp() {
      return fp;
    }

    public void setFp(int fp) {
      this.fp = fp;
    }
  }

  public static class Registers {
    private final long[] data = new long[4096];
    private final Object[] refs = new Object[4096];
    private final boolean[] isDouble = new boolean[4096];
    private final boolean[] isObject = new boolean[4096];
    private int sp = 0;

    public void push(long val) {
      data[sp] = val;
      refs[sp] = null;
      isDouble[sp] = false;
      isObject[sp] = false;
      sp++;
    }

    public void push(double val) {
      data[sp] = Double.doubleToRawLongBits(val);
      refs[sp] = null;
      isDouble[sp] = true;
      isObject[sp] = false;
      sp++;
    }

    public void pushObject(Object obj) {
      refs[sp] = obj;
      isObject[sp] = true;
      isDouble[sp] = false;
      sp++;
    }

    public long popLong() {
      if (sp <= 0) return 0L;
      sp--;
      if (isDouble[sp]) return (long) Double.longBitsToDouble(data[sp]);
      return data[sp];
    }

    public double popDouble() {
      if (sp <= 0) return 0.0;
      sp--;
      if (!isDouble[sp]) return (double) data[sp];
      return Double.longBitsToDouble(data[sp]);
    }

    public Object popObject() {
      if (sp <= 0) return null;
      sp--;
      Object o = refs[sp];
      refs[sp] = null;
      return o;
    }

    public Object pop() {
      if (sp <= 0) return 0L;
      sp--;
      if (isObject[sp]) {
        Object o = refs[sp];
        refs[sp] = null;
        return o;
      }
      if (isDouble[sp]) return Double.longBitsToDouble(data[sp]);
      return data[sp];
    }

    public void pop(int n) {
      sp = Math.max(0, sp - n);
    }

    public long popAsLong() {
      if (sp <= 0) return 0L;
      int idx = sp - 1;
      if (isObject[idx]) {
        Object o = popObject();
        return switch (o) {
          case null -> 0L;
          case Boolean b -> (Boolean) b ? 1L : 0L;
          case ChuckDuration cd -> (long) cd.samples();
          case Number n -> ((Number) n).longValue();
          default -> 1L;
        };
      }
      return popLong();
    }

    public double popAsDouble() {
      if (sp <= 0) return 0.0;
      int idx = sp - 1;
      if (isObject[idx]) {
        Object o = popObject();
        return switch (o) {
          case null -> 0.0;
          case ChuckDuration cd -> cd.samples();
          case Number n -> ((Number) n).doubleValue();
          default -> 1.0;
        };
      }
      return popDouble();
    }

    public boolean isObject(int idx) {
      int t = sp - 1 - idx;
      return t >= 0 && isObject[t];
    }

    public boolean isDouble(int idx) {
      int t = sp - 1 - idx;
      return t >= 0 && isDouble[t];
    }

    public boolean isObjectAt(int idx) {
      return idx >= 0 && idx < 4096 && isObject[idx];
    }

    public boolean isDoubleAt(int idx) {
      return idx >= 0 && idx < 4096 && isDouble[idx];
    }

    public long peekLong(int idx) {
      int t = sp - 1 - idx;
      if (t < 0) return 0L;
      if (isDouble[t]) return (long) Double.longBitsToDouble(data[t]);
      return data[t];
    }

    public Object peekObject(int idx) {
      int t = sp - 1 - idx;
      return (t >= 0) ? refs[t] : null;
    }

    public double peekAsDouble(int idx) {
      int t = sp - 1 - idx;
      if (t < 0) return 0.0;
      if (isDouble[t]) return Double.longBitsToDouble(data[t]);
      return (double) data[t];
    }

    public void dup() {
      if (sp <= 0) return;
      int src = sp - 1;
      data[sp] = data[src];
      refs[sp] = refs[src];
      isDouble[sp] = isDouble[src];
      isObject[sp] = isObject[src];
      sp++;
    }

    public void dup2() {
      if (sp <= 1) return;
      int s1 = sp - 2, s2 = sp - 1;
      data[sp] = data[s1];
      refs[sp] = refs[s1];
      isDouble[sp] = isDouble[s1];
      isObject[sp] = isObject[s1];
      data[sp + 1] = data[s2];
      refs[sp + 1] = refs[s2];
      isDouble[sp + 1] = isDouble[s2];
      isObject[sp + 1] = isObject[s2];
      sp += 2;
    }

    public void swap() {
      if (sp <= 1) return;
      int i1 = sp - 1, i2 = sp - 2;
      long tD = data[i1];
      data[i1] = data[i2];
      data[i2] = tD;
      Object tR = refs[i1];
      refs[i1] = refs[i2];
      refs[i2] = tR;
      boolean tDb = isDouble[i1];
      isDouble[i1] = isDouble[i2];
      isDouble[i2] = tDb;
      boolean tOb = isObject[i1];
      isObject[i1] = isObject[i2];
      isObject[i2] = tOb;
    }

    public void rot() {
      if (sp <= 2) return;
      int i1 = sp - 1, i2 = sp - 2, i3 = sp - 3;
      long tD = data[i1];
      Object tR = refs[i1];
      boolean tDb = isDouble[i1];
      boolean tOb = isObject[i1];
      data[i1] = data[i2];
      refs[i1] = refs[i2];
      isDouble[i1] = isDouble[i2];
      isObject[i1] = isObject[i2];
      data[i2] = data[i3];
      refs[i2] = refs[i3];
      isDouble[i2] = isDouble[i3];
      isObject[i2] = isObject[i3];
      data[i3] = tD;
      refs[i3] = tR;
      isDouble[i3] = tDb;
      isObject[i3] = tOb;
    }

    public int getSp() {
      return sp;
    }

    public void setSp(int sp) {
      this.sp = Math.max(0, Math.min(4096, sp));
    }

    public long getData(int idx) {
      return (idx >= 0 && idx < 4096) ? data[idx] : 0L;
    }

    public Object getRef(int idx) {
      return (idx >= 0 && idx < 4096) ? refs[idx] : null;
    }
  }
}
