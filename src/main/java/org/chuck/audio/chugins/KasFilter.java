package org.chuck.audio.chugins;

import org.chuck.audio.ChuckUGen;

/**
 * KasFilter — under-sampling resonant lowpass filter with cosine crossfading.
 *
 * <p>Two sample-and-holds crossfaded by a cosine at the cutoff frequency, creating an infinitely
 * steep (but aliased) cutoff. Negative feedback gives resonance. Waveshaping of the crossfade
 * signal adds "accent" distortion.
 *
 * <p>Port of chugins/KasFilter by Kassen Oud.
 */
public class KasFilter extends ChuckUGen {
  private final double phasePerSample;
  private double freq = 440.0;
  private double resonance = 0.0; // stored negative internally
  private double accent = 1.0; // stored as (userAccent + 1) internally

  private double storeA = 0.0;
  private double storeB = 0.0;
  private double lastIn = 0.0;
  private double phase = 0.0;

  public KasFilter(float sampleRate) {
    this.phasePerSample = Math.PI / sampleRate;
  }

  @Override
  protected float compute(float input, long systemTime) {
    double in = input;
    double lastPhase = phase;

    if (freq > 0) {
      double phaseInc = phasePerSample * freq;
      phase += phaseInc;

      if (phase > 2.0 * Math.PI) {
        phase -= 2.0 * Math.PI;
        double interp = phase / phaseInc;
        storeB = in * interp + lastIn * (1.0 - interp);
        storeB += resonance * storeA; // resonance is negative internally
        storeB = Math.max(-1.0, Math.min(1.0, storeB));
      } else if (phase > Math.PI && lastPhase < Math.PI) {
        double interp = (phase - Math.PI) / phaseInc;
        storeA = in * interp + lastIn * (1.0 - interp);
        storeA += resonance * storeB;
        storeA = Math.max(-1.0, Math.min(1.0, storeA));
      }
    }

    double mix = Math.cos(phase);
    double absMix = Math.abs(mix);
    mix = 0.5 + 0.5 * mix * (absMix + accent * (1.0 - absMix));

    lastIn = in;
    return (float) (storeA * mix + storeB * (1.0 - mix));
  }

  public double freq(double f) {
    this.freq = Math.abs(f);
    return this.freq;
  }

  public double freq() {
    return freq;
  }

  public double resonance(double r) {
    r = Math.max(0.0, Math.min(0.95, r));
    this.resonance = -r; // negative feedback
    return r;
  }

  public double resonance() {
    return -resonance;
  }

  /** Accent = waveshaping amount [0-1]. 0 = pure crossfade; 1 ≈ undersampling. */
  public double accent(double a) {
    a = Math.max(0.0, Math.min(1.0, a));
    this.accent = a + 1.0;
    return a;
  }

  public double accent() {
    return accent - 1.0;
  }
}
