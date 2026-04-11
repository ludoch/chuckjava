package org.chuck.audio.filter;

import org.chuck.audio.ChuckUGen;

/**
 * Biquad (second-order IIR) resonator filter. Supports prad (pole radius), pfreq (pole frequency
 * Hz), gain, and eqzs.
 */
public class BiQuad extends ChuckUGen {
  private double prad = 0.0;
  private double pfreq = 0.0;
  private boolean eqzs = false;

  // Biquad state
  private double x1 = 0, x2 = 0;
  private double y1 = 0, y2 = 0;

  // Coefficients (direct form I)
  private double b0 = 1, b1 = 0, b2 = 0; // feedforward
  private double a1 = 0, a2 = 0; // feedback (a0 = 1 normalised)

  private final float sampleRate;

  public BiQuad(float sampleRate) {
    this.sampleRate = sampleRate;
  }

  public void setPrad(double r) {
    this.prad = r;
    updateCoeffs();
  }

  public void setPfreq(double f) {
    this.pfreq = f;
    updateCoeffs();
  }

  public void setEqzs(double v) {
    this.eqzs = v != 0.0;
    updateCoeffs();
  }

  /** ChucK-style: z.radius(0.99) */
  public double radius(double r) {
    setPrad(r);
    return r;
  }

  /** ChucK-style: z.freq(440) */
  public double freq(double f) {
    setPfreq(f);
    return f;
  }

  /** ChucK-style: z.norm(1) */
  public double norm(double v) {
    setEqzs(v);
    return v;
  }

  public double getPrad() {
    return prad;
  }

  public double getPfreq() {
    return pfreq;
  }

  private void updateCoeffs() {
    if (pfreq <= 0 || sampleRate <= 0) {
      b0 = 1;
      b1 = 0;
      b2 = 0;
      a1 = 0;
      a2 = 0;
      return;
    }
    double omega = 2.0 * Math.PI * pfreq / sampleRate;
    a1 = -2.0 * prad * Math.cos(omega);
    a2 = prad * prad;
    if (eqzs) {
      b0 = 1.0;
      b1 = 0.0;
      b2 = -1.0;
    } else {
      b0 = 1;
      b1 = 0;
      b2 = 0;
    }
  }

  @Override
  protected float compute(float input, long systemTime) {
    double x0 = input;
    double y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
    x2 = x1;
    x1 = x0;
    y2 = y1;
    y1 = y0;
    return (float) y0;
  }
}
