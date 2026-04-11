package org.chuck.audio.filter;

import org.chuck.audio.ChuckUGen;

/** A resonance filter. Adapted from SuperCollider's ResonZ via ChucK. */
public class ResonZ extends ChuckUGen {
  private float freq = 220.0f;
  private float Q = 1.0f;
  private float a0, b1, b2;
  private float y1, y2;
  private final float sampleRate;

  public ResonZ(float sampleRate) {
    this.sampleRate = sampleRate;
    set(freq, Q);
  }

  public void set(float freq, float Q) {
    this.freq = freq;
    this.Q = Q;

    double radiansPerSample = 2.0 * Math.PI / sampleRate;
    double pfreq = freq * radiansPerSample;
    double B = pfreq / Q;
    double R = 1.0 - B * 0.5;
    double R2 = 2.0 * R;
    double R22 = R * R;
    double cost = (R2 * Math.cos(pfreq)) / (1.0 + R22);

    this.b1 = (float) (R2 * cost);
    this.b2 = (float) (-R22);
    this.a0 = (float) ((1.0 - R22) * 0.5);
  }

  public void setFreq(float freq) {
    set(freq, this.Q);
  }

  public void setQ(float Q) {
    set(this.freq, Q);
  }

  @Override
  protected float compute(float input, long systemTime) {
    float y0 = input + b1 * y1 + b2 * y2;
    float result = a0 * (y0 - y2);
    y2 = y1;
    y1 = y0;
    return result;
  }
}
