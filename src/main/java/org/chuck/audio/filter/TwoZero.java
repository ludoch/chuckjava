package org.chuck.audio.filter;

import org.chuck.audio.ChuckUGen;

/**
 * Two-zero (FIR) notch filter. H(z) = b0 + b1*z^-1 + b2*z^-2
 *
 * <p>Use setNotch(freq, radius) for a normalized notch, or set raw b0/b1/b2.
 */
public class TwoZero extends ChuckUGen {
  private double b0 = 1.0;
  private double b1 = 0.0;
  private double b2 = 0.0;

  private double in1 = 0.0; // x[n-1]
  private double in2 = 0.0; // x[n-2]

  private double notchFreq = 440.0;
  private double notchRad = 0.0;

  private final float sampleRate;

  public TwoZero(float sampleRate) {
    this.sampleRate = sampleRate;
  }

  /**
   * Place two zeros at the given frequency (Hz) and radius, normalized so gain = 1 everywhere
   * except the notch.
   */
  public void setNotch(double frequency, double radius) {
    notchFreq = frequency;
    notchRad = radius;
    b2 = radius * radius;
    b1 = -2.0 * radius * Math.cos(2.0 * Math.PI * frequency / sampleRate);
    // Normalize for unity gain
    double norm;
    if (b1 > 0.0) norm = 1.0 / (1.0 + b1 + b2); // peak at z = -1
    else norm = 1.0 / (1.0 - b1 + b2); // peak at z = +1
    b0 = norm;
    b1 *= norm;
    b2 *= norm;
  }

  // ChucK-style accessors
  public double freq(double f) {
    notchFreq = f;
    setNotch(notchFreq, notchRad);
    return f;
  }

  public double freq() {
    return notchFreq;
  }

  public double radius(double r) {
    notchRad = r;
    setNotch(notchFreq, notchRad);
    return r;
  }

  public double radius() {
    return notchRad;
  }

  // Raw coefficient setters
  public void setB0(double v) {
    b0 = v;
  }

  public void setB1(double v) {
    b1 = v;
  }

  public void setB2(double v) {
    b2 = v;
  }

  @Override
  protected float compute(float input, long systemTime) {
    double y = b0 * input + b1 * in1 + b2 * in2;
    in2 = in1;
    in1 = input;
    return (float) y;
  }
}
