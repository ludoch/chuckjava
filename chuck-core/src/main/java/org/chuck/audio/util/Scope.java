package org.chuck.audio.util;

import org.chuck.audio.UAna;

/** A Unit Analyzer that captures raw time-domain samples for an Oscilloscope. */
public class Scope extends UAna {
  private float[] ring;
  private int writePos = 0;
  private int size;

  public Scope() {
    this(1024);
  }

  public Scope(int size) {
    setSize(size);
  }

  public void setSize(int size) {
    this.size = size;
    this.ring = new float[size];
    this.writePos = 0;
  }

  public int getWindowSize() {
    return size;
  }

  public void setWindowSize(int size) {
    setSize(size);
  }

  @Override
  protected float compute(float input, long systemTime) {
    ring[writePos] = input;
    writePos = (writePos + 1) % size;
    return input;
  }

  @Override
  protected void computeUAna() {
    // Copy raw samples in order (oldest to newest) for oscilloscope display
    float[] samples = new float[size];
    for (int i = 0; i < size; i++) {
      samples[i] = ring[(writePos + i) % size];
    }
    lastBlob.setFvals(samples);
  }
}
