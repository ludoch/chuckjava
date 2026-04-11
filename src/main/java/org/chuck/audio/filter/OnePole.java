package org.chuck.audio.filter;

import org.chuck.audio.ChuckUGen;

/** A one-pole digital filter. y[n] = b0 * x[n] - a1 * y[n-1] */
public class OnePole extends ChuckUGen {
  private float b0 = 1.0f;
  private float a1 = 0.0f;
  private float lastOutput = 0.0f;

  public void setB0(float b0) {
    this.b0 = b0;
  }

  public void setA1(float a1) {
    this.a1 = a1;
  }

  public void setPole(float pole) {
    if (pole > 0.0f) b0 = 1.0f - pole;
    else b0 = 1.0f + pole;
    a1 = -pole;
  }

  @Override
  protected float compute(float input, long systemTime) {
    lastOutput = b0 * input - a1 * lastOutput;
    return lastOutput;
  }
}
