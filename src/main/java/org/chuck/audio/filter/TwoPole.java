package org.chuck.audio.filter;

import org.chuck.audio.ChuckUGen;

/**
 * Two-pole resonance filter. H(z) = b0 / (1 + a1*z^-1 + a2*z^-2)
 *
 * <p>Use setResonance(freq, radius, normalize) for convenient resonant peak setup, or set raw
 * coefficients via setB0/setA1/setA2.
 */
public class TwoPole extends ChuckUGen {
  private double b0 = 1.0;
  private double a1 = 0.0;
  private double a2 = 0.0;

  private double out1 = 0.0; // y[n-1]
  private double out2 = 0.0; // y[n-2]

  private double resFreq = 440.0;
  private double resRad = 0.0;
  private boolean resNorm = false;

  private final float sampleRate;

  public TwoPole(float sampleRate) {
    this.sampleRate = sampleRate;
  }

  /** Set resonance at given frequency (Hz) and pole radius [0,1). */
  public void setResonance(double frequency, double radius, boolean normalize) {
    resFreq = frequency;
    resRad = radius;
    resNorm = normalize;
    a2 = radius * radius;
    a1 = -2.0 * radius * Math.cos(2.0 * Math.PI * frequency / sampleRate);
    if (normalize) {
      // Unity gain at resonance
      double real =
          1.0 - radius + (a2 - radius) * Math.cos(2.0 * Math.PI * 2.0 * frequency / sampleRate);
      double imag = (a2 - radius) * Math.sin(2.0 * Math.PI * 2.0 * frequency / sampleRate);
      b0 = Math.sqrt(real * real + imag * imag);
    }
  }

  public void setResonance(double frequency, double radius) {
    setResonance(frequency, radius, resNorm);
  }

  // ChucK-style accessors
  public double freq(double f) {
    resFreq = f;
    setResonance(resFreq, resRad, resNorm);
    return f;
  }

  public double freq() {
    return resFreq;
  }

  public double radius(double r) {
    resRad = r;
    setResonance(resFreq, resRad, resNorm);
    return r;
  }

  public double radius() {
    return resRad;
  }

  public double norm(double v) {
    setResonance(resFreq, resRad, v != 0.0);
    return v;
  }

  // Raw coefficient setters
  public void setB0(double v) {
    b0 = v;
  }

  public void setA1(double v) {
    a1 = v;
  }

  public void setA2(double v) {
    a2 = v;
  }

  @Override
  protected float compute(float input, long systemTime) {
    double y = b0 * input - a1 * out1 - a2 * out2;
    out2 = out1;
    out1 = y;
    return (float) y;
  }
}
