package org.chuck.audio.chugins;

import org.chuck.audio.ChuckUGen;

/**
 * MagicSine — efficient sine oscillator using the "magic circle" algorithm.
 *
 * <p>Uses 4 multiplies + 2 adds per sample instead of a table lookup or Math.sin(). About 25%
 * faster than SinOsc for a fixed-frequency sine. Cannot set phase directly.
 *
 * <p>Port of chugins/MagicSine by Spencer Salazar.
 */
public class MagicSine extends ChuckUGen {
  private final float sampleRate;
  private float freq = 220.0f;
  private double epsilon; // 2 * sin(pi * freq / sr)
  private double x = 1.0; // cosine state
  private double y = 0.0; // sine state (output)

  public MagicSine(float sampleRate) {
    this.sampleRate = sampleRate;
    setFreq(220.0f);
  }

  private void setFreq(float f) {
    this.freq = f;
    this.epsilon = 2.0 * Math.sin(Math.PI * f / sampleRate);
  }

  @Override
  protected float compute(float input, long systemTime) {
    x = x + epsilon * y;
    y = -epsilon * x + y;
    return (float) y;
  }

  public double freq(double f) {
    setFreq((float) f);
    return f;
  }

  public double freq() {
    return freq;
  }
}
