package org.chuck.audio.chugins;

import org.chuck.audio.ChuckUGen;

/**
 * Perlin — Perlin coherent noise oscillator.
 *
 * <p>Generates 1D Perlin noise sampled at a frequency-controlled phase rate. Produces smooth
 * band-limited noise with a characteristic "organic" quality, unlike white noise.
 *
 * <p>Port of chugins/Perlin using Ken Perlin's original algorithm (paulbourke.net adaptation).
 */
public class Perlin extends ChuckUGen {
  private static final int B = 0x100;
  private static final int BM = 0xff;
  private static final int N = 0x1000;

  private final float sampleRate;
  private float freq = 220.0f;
  private double phase = 0.0;

  // Permutation and gradient tables
  private final int[] p = new int[B + B + 2];
  private final double[] g1 = new double[B + B + 2];
  private boolean initialized = false;

  public Perlin(float sampleRate) {
    this.sampleRate = sampleRate;
    init();
  }

  private void init() {
    java.util.Random rng = new java.util.Random();
    // initialize gradient table
    for (int i = 0; i < B; i++) {
      p[i] = i;
      g1[i] = (rng.nextInt(B + B) - B) / (double) B;
    }
    // shuffle permutation
    for (int i = B - 1; i > 0; i--) {
      int j = rng.nextInt(i + 1);
      int tmp = p[i];
      p[i] = p[j];
      p[j] = tmp;
    }
    // extend tables
    for (int i = 0; i < B + 2; i++) {
      p[B + i] = p[i];
      g1[B + i] = g1[i];
    }
    initialized = true;
  }

  private static double sCurve(double t) {
    return t * t * (3.0 - 2.0 * t);
  }

  private double noise1(double arg) {
    double t = arg + N;
    int bx0 = ((int) t) & BM;
    int bx1 = (bx0 + 1) & BM;
    double rx0 = t - (int) t;
    double rx1 = rx0 - 1.0;
    double sx = sCurve(rx0);
    double u = rx0 * g1[p[bx0]];
    double v = rx1 * g1[p[bx1]];
    return u + sx * (v - u);
  }

  @Override
  protected float compute(float input, long systemTime) {
    float out = (float) noise1(phase);
    phase += freq / sampleRate;
    return out;
  }

  public double freq(double f) {
    this.freq = (float) f;
    return f;
  }

  public double freq() {
    return freq;
  }

  /** Direct 1D noise query at position x (ignores oscillator phase). */
  public double noise(double x) {
    return noise1(x);
  }
}
