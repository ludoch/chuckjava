package org.chuck.core;

/** Base class for all objects in the ChucK Virtual Machine. */
public class ChuckObject {
  private final ChuckType type;
  private boolean buffered = false;

  // Member data for user-defined classes
  private final long[] data;
  private final boolean[] isDouble;
  private final Object[] refs;

  public ChuckObject(ChuckType type) {
    this.type = type;
    this.data = new long[type.getDataSize()];
    this.isDouble = new boolean[type.getDataSize()];
    this.refs = new Object[type.getRefSize()];
  }

  public ChuckType getType() {
    return type;
  }

  public boolean buffered() {
    return buffered;
  }

  public void setBuffered(boolean b) {
    this.buffered = b;
  }

  public void setData(int index, long value) {
    if (index < data.length) {
      data[index] = value;
      isDouble[index] = false;
    }
  }

  public void setData(int index, double value) {
    if (index < data.length) {
      // CRITICAL: Set the flag FIRST so any subclass overriding setData(int, long)
      // sees the correct type when calling getDataAsDouble().
      isDouble[index] = true;
      data[index] = Double.doubleToRawLongBits(value);

      // Still trigger the long variant for subclass hooks, but we must
      // avoid it clearing the flag we just set.
      // We'll call a private internal setter or just manually trigger the hook.
      triggerDataHook(index, data[index]);
    }
  }

  /** Overridable hook for subclasses to react to data changes. */
  protected void triggerDataHook(int index, long value) {
    // Default: do nothing. Subclasses like Osc will override this.
  }

  public boolean isDouble(int index) {
    return (index < isDouble.length) ? isDouble[index] : false;
  }

  public long getData(int index) {
    return (index < data.length) ? data[index] : 0;
  }

  /** Gets data at index and treats it as a double. */
  public double getDataAsDouble(int index) {
    if (index >= data.length) return 0.0;
    long raw = data[index];
    if (isDouble[index]) return Double.longBitsToDouble(raw);
    return (double) raw;
  }

  public void setRef(int index, Object obj) {
    if (index < refs.length) {
      refs[index] = obj;
    }
  }

  public Object getRef(int index) {
    return (index < refs.length) ? refs[index] : null;
  }
}
