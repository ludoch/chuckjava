package org.chuck.audio.chugins;

import org.chuck.audio.ChuckUGen;

/**
 * ExpEnv — simple single time-constant exponential decay envelope.
 *
 * <p>Multiplies the input by a decaying envelope value each sample: {@code value *= radius}.
 * Especially useful for modal synthesis (one per mode) or noise excitation pulses.
 *
 * <p>Port of chugins/ExpEnv by Perry R. Cook.
 */
public class ExpEnv extends ChuckUGen {
  private final float sampleRate;
  private double radius = 0.999;
  private double value = 0.0;
  private double t60 = 0.0;

  public ExpEnv(float sampleRate) {
    this.sampleRate = sampleRate;
  }

  @Override
  protected float compute(float input, long systemTime) {
    value *= radius;
    return (float) (input * value);
  }

  public double value(double v) {
    this.value = v;
    return v;
  }

  public double value() {
    return value;
  }

  public double radius(double r) {
    this.radius = r;
    if (r < 1.0) {
      this.t60 = -3.0 / Math.log10(r) / sampleRate;
    } else {
      this.t60 = 0.0;
    }
    return r;
  }

  public double radius() {
    return radius;
  }

  /** Set T60 decay time in samples. */
  public double T60(double samps) {
    this.t60 = samps;
    if (samps > 0) {
      this.radius = Math.pow(10.0, -3.0 / samps);
    } else {
      this.radius = 1.0;
    }
    return samps;
  }

  public double T60() {
    return t60;
  }

  /** Trigger: sets envelope value to 1.0. */
  public int keyOn() {
    this.value = 1.0;
    return 1;
  }

  public int keyOn(int ignored) {
    return keyOn();
  }
}
