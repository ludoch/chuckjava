package org.chuck.audio.filter;

import org.chuck.audio.ChuckUGen;

/** 2nd order Butterworth Low Pass Filter. Matches native ChucK (SuperCollider-style). */
public class LPF extends ChuckUGen {
  private double b0, b1, b2, a1, a2;
  private double x1, x2, y1, y2;
  private double freq = 10000.0;
  private double Q = 1.0;
  private final float sampleRate;

  public LPF() {
    this(44100.0f, true);
  }

  public LPF(float sampleRate) {
    this(sampleRate, true);
  }

  public LPF(float sampleRate, boolean autoRegister) {
    super(autoRegister);
    this.sampleRate = sampleRate;
    set(10000.0, 1.0);
  }

  public double freq(double f) {
    set(f, Q);
    return f;
  }

  public double Q(double q) {
    set(freq, q);
    return q;
  }

  public void setCutoff(float f) {
    freq(f);
  }

  private void set(double f, double q) {
    this.freq = f;
    this.Q = q;

    double fr = Math.PI * f / sampleRate;
    double C = 1.0 / Math.tan(fr);
    double root2C = Math.sqrt(2.0) * C;
    double C2 = C * C;

    double m_a0 = 1.0 / (1.0 + root2C + C2);

    b0 = m_a0;
    b1 = 2.0 * m_a0;
    b2 = m_a0;
    a1 = 2.0 * (1.0 - C2) * m_a0;
    a2 = (1.0 - root2C + C2) * m_a0;
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
