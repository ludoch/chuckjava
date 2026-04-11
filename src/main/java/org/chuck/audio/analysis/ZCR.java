package org.chuck.audio.analysis;

import org.chuck.audio.UAna;
import org.chuck.audio.util.Complex;

/**
 * ZCR — Zero Crossing Rate UAna. Counts zero crossings per analysis frame and outputs the rate (0.0
 * to 1.0). UAna-style: accumulates samples in a buffer, computes rate when full.
 */
public class ZCR extends UAna {
  private int frameSize;
  private float[] buffer;
  private int bufIdx = 0;
  private float result = 0.0f;
  private float lastSample = 0.0f;

  public ZCR() {
    this(1024);
  }

  public ZCR(int frameSize) {
    this.frameSize = frameSize;
    this.buffer = new float[frameSize];
  }

  public void setFrameSize(int size) {
    this.frameSize = size;
    this.buffer = new float[size];
    this.bufIdx = 0;
  }

  public int getFrameSize() {
    return frameSize;
  }

  /** Returns the last computed ZCR value (crossings per sample, 0.0–1.0). */
  public float getZCR() {
    return result;
  }

  /** Feed a single sample directly (for testing without a UGen graph). */
  public float addSample(float sample) {
    return compute(sample, 0);
  }

  /** ChucK-style last() accessor returning ZCR result. */
  @Override
  public float last() {
    return result;
  }

  @Override
  protected float compute(float input, long systemTime) {
    buffer[bufIdx++] = input;
    if (bufIdx >= frameSize) {
      int crossings = 0;
      float prev = lastSample;
      for (float s : buffer) {
        if ((prev <= 0 && s > 0) || (prev >= 0 && s < 0)) crossings++;
        prev = s;
      }
      lastSample = buffer[frameSize - 1];
      result = (float) crossings / frameSize;
      bufIdx = 0;
    }
    return result;
  }

  @Override
  protected void computeUAna() {
    // Expose result via blob for UAna-style upchuck()
    lastBlob.setCvals(java.util.Collections.singletonList(new Complex(result, 0)));
  }
}
