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
      triggerDataHook(index, value);
    }
  }

  public void setData(int index, double value) {
    if (index < data.length) {
      isDouble[index] = true;
      data[index] = Double.doubleToRawLongBits(value);
      triggerDataHook(index, data[index]);
    }
  }

  /**
   * Internal data setter that doesn't reset the isDouble flag. Used by subclasses in their hooks if
   * they need to pass-through to super.
   */
  public void setDataInternal(int index, long value) {
    if (index < data.length) {
      data[index] = value;
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
