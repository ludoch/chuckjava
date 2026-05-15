package org.chuck.audio.filter;

import org.chuck.audio.ChuckUGen;

/** Resonance filter with equal-gain zeroes. Matches native ChucK (SuperCollider-style). */
public class ResonZ extends ChuckUGen {
  private double b0, b1, b2, a1, a2;
  private double x1, x2, y1, y2;
  private double freq = 440.0;
  private double Q = 1.0;
  private final float sampleRate;

  public ResonZ() {
    this(44100.0f, true);
  }

  public ResonZ(float sampleRate) {
    this(sampleRate, true);
  }

  public ResonZ(float sampleRate, boolean autoRegister) {
    super(autoRegister);
    this.sampleRate = sampleRate;
    set(440.0, 1.0);
  }

  public double freq(double f) {
    set(f, Q);
    return f;
  }

  public double freq() {
    return freq;
  }

  public double Q(double q) {
    set(freq, q);
    return q;
  }

  public double Q() {
    return Q;
  }

  public void setFreq(float f) {
    freq(f);
  }

  public void setQ(float q) {
    Q(q);
  }

  private void set(double f, double q) {
    this.freq = f;
    this.Q = q;

    double fr = 2.0 * Math.PI * f / sampleRate;
    double B = fr / q;
    double R = 1.0 - 0.5 * B;
    double R2 = R * R;

    double m_a0 = 0.5 * (1.0 - R2);

    b0 = m_a0;
    b1 = 0;
    b2 = -m_a0;
    a1 = -(4.0 * R2 * Math.cos(fr)) / (1.0 + R2);
    a2 = R2;
  }

  @Override
  protected float compute(float input, long systemTime) {
    double out = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
    x2 = x1;
    x1 = input;
    y2 = y1;
    y1 = out;
    return (float) out;
  }
}
