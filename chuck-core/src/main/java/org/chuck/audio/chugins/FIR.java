package org.chuck.audio.chugins;

import org.chuck.audio.ChuckUGen;

/**
 * FIR — general-purpose FIR filter with sinc/gaussian LP design helpers.
 *
 * <p>Supports manual coefficient setting as well as built-in sinc LP, gaussian LP, HP (cosine
 * modulate to Nyquist), and BP (cosine modulate to target freq) designs. Default: 4-tap moving
 * average.
 *
 * <p>Port of chugins/FIR by Perry R. Cook.
 */
public class FIR extends ChuckUGen {
  private final float sampleRate;
  private float[] coeff;
  private float[] buffer;
  private int order;

  public FIR(float sampleRate) {
    this.sampleRate = sampleRate;
    order(4);
  }

  @Override
  protected float compute(float input, long systemTime) {
    float out = 0.0f;
    for (int i = 0; i < order; i++) {
      out += coeff[i] * buffer[i];
    }
    // shift delay line
    for (int i = order - 1; i > 0; i--) {
      buffer[i] = buffer[i - 1];
    }
    buffer[0] = input;
    return out;
  }

  public final int order(int n) {
    this.order = Math.max(1, n);
    this.coeff = new float[order];
    this.buffer = new float[order];
    float avg = 1.0f / order;
    for (int i = 0; i < order; i++) {
      coeff[i] = avg;
    }
    return order;
  }

  public int order() {
    return order;
  }

  public float coeff(int idx, float val) {
    if (idx >= 0 && idx < order) coeff[idx] = val;
    return idx >= 0 && idx < order ? coeff[idx] : 0.0f;
  }

  public float coeff(int idx) {
    return idx >= 0 && idx < order ? coeff[idx] : 0.0f;
  }

  /**
   * Design a sinc-windowed LP filter. cutoff is a divisor: bandwidth = SR / (2*cutoff), e.g.
   * cutoff=4 → LP at SR/8.
   */
  public int sinc(float cutoff) {
    if (cutoff < 1.0f) cutoff = 1.0f;
    int half = order / 2;
    coeff[half] = 1.0f;
    for (int i = 1; i < half; i++) {
      double phase = i * Math.PI / cutoff;
      float v = (float) (Math.sin(phase) / phase);
      coeff[half + i] = v;
      coeff[half - i] = v;
    }
    hanning();
    normalize();
    return order;
  }

  /** Design a gaussian-windowed LP smoother. bandwidth controls rolloff (min 2). */
  public int gaussian(float bandwidth) {
    if (bandwidth < 2.0f) bandwidth = 2.0f;
    float temp = (float) (6.28 / bandwidth / bandwidth);
    int exparg = order / 2;
    for (int i = 0; i < order; i++) {
      coeff[i] = (float) Math.exp(-exparg * exparg * temp);
      exparg--;
    }
    hanning();
    normalize();
    return order;
  }

  /** Cosine-modulate current LP to HP (Nyquist). */
  public int hpHetero() {
    float phase = 1.0f;
    for (int i = 0; i < order; i++) {
      coeff[i] *= phase;
      phase *= -1.0f;
    }
    normalize();
    return order;
  }

  /** Cosine-modulate current LP to BP centered at centerFreq. */
  public int bpHetero(float centerFreq) {
    double cf = Math.PI * centerFreq;
    for (int i = 0; i < order; i++) {
      coeff[i] = (float) (coeff[i] * Math.cos(i * cf));
    }
    normalize();
    return order;
  }

  private void hanning() {
    for (int i = 0; i < order; i++) {
      coeff[i] *= (float) (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / order)));
    }
  }

  private void normalize() {
    double power = 0.0;
    for (float c : coeff) power += c * c;
    power = Math.sqrt(power);
    if (power > 0.0) {
      for (int i = 0; i < order; i++) coeff[i] /= power;
    }
  }
}
