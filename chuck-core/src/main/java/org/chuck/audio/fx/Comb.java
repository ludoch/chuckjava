package org.chuck.audio.fx;

import org.chuck.audio.ChuckUGen;

/**
 * A comb filter UGen. Adapted from STK. Uses double precision internally to prevent limit cycles.
 */
public class Comb extends ChuckUGen {
  private final double[] buffer;
  private int writePos = 0;
  private int delaySamples;
  private double coefficient = 0.7;

  public Comb(int maxDelaySamples) {
    this(maxDelaySamples, true);
  }

  public Comb(int maxDelaySamples, boolean autoRegister) {
    super(autoRegister);
    this.buffer = new double[maxDelaySamples];
    this.delaySamples = maxDelaySamples;
  }

  public void delay(double samples) {
    int s = (int) samples;
    if (s >= buffer.length) s = buffer.length - 1;
    if (s < 0) s = 0;
    this.delaySamples = s;
  }

  public void setCoefficient(double c) {
    this.coefficient = c;
  }

  @Override
  protected float compute(float input, long systemTime) {
    int readPos = (writePos - delaySamples + buffer.length) % buffer.length;
    double temp = buffer[readPos];
    double out = input + coefficient * temp;

    buffer[writePos] = out;
    writePos = (writePos + 1) % buffer.length;

    return (float) temp;
  }
}
