package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/**
 * Exponential ADSR Envelope modeled after the Synthstrom Deluge.
 *
 * <p>Unlike the standard linear Adsr, this envelope uses exponential curves for all stages. It also
 * features a FAST_RELEASE stage (typically ~5ms) intended to be triggered during voice stealing to
 * prevent clicks while avoiding long release tails.
 */
@doc("Exponential ADSR modeled after the Deluge (with fast-release for voice steal).")
public class DelugeAdsr extends ChuckUGen {

  public static final int IDLE = 0;
  public static final int ATTACK = 1;
  public static final int DECAY = 2;
  public static final int SUSTAIN = 3;
  public static final int RELEASE = 4;
  public static final int FAST_RELEASE = 5;

  private float sampleRate;

  private volatile int state = IDLE;
  private double value = 0.0;
  private double target = 0.0;

  // Durations in samples
  private double attackRate = 0.0;
  private double decayRate = 0.0;
  private double releaseRate = 0.0;
  private double fastReleaseRate = 0.0;

  private double sustainLevel = 0.7;

  // Coefficients for exponential filters
  private double attackCoef = 0.0;
  private double decayCoef = 0.0;
  private double releaseCoef = 0.0;
  private double fastReleaseCoef = 0.0;

  // Base offset for attack to ensure it reaches 1.0 in finite time
  private static final double TARGET_RATIO_A = 0.3;
  private static final double TARGET_RATIO_DR = 0.0001;

  private double attackBase = 0.0;
  private double decayBase = 0.0;
  private double releaseBase = 0.0;
  private double fastReleaseBase = 0.0;

  public DelugeAdsr() {
    this(org.chuck.core.ChuckVM.CURRENT_VM.get().getSampleRate());
  }

  public DelugeAdsr(float sampleRate) {
    this.sampleRate = sampleRate;

    // Default fast release is ~5ms
    fastReleaseTime(0.005);
    set(0.01, 0.1, 0.7, 0.2); // 10ms A, 100ms D, 0.7 S, 200ms R
  }

  public double attackTime(double timeSeconds) {
    double timeInSamples = timeSeconds * sampleRate;
    this.attackRate = timeInSamples;
    if (timeInSamples > 0.0) {
      attackCoef = Math.exp(-Math.log((1.0 + TARGET_RATIO_A) / TARGET_RATIO_A) / timeInSamples);
      attackBase = (1.0 + TARGET_RATIO_A) * (1.0 - attackCoef);
    } else {
      attackCoef = 0.0;
      attackBase = 1.0;
    }
    return timeSeconds;
  }

  public double decayTime(double timeSeconds) {
    double timeInSamples = timeSeconds * sampleRate;
    this.decayRate = timeInSamples;
    if (timeInSamples > 0.0) {
      decayCoef = Math.exp(-Math.log((1.0 + TARGET_RATIO_DR) / TARGET_RATIO_DR) / timeInSamples);
      decayBase = (sustainLevel - TARGET_RATIO_DR) * (1.0 - decayCoef);
    } else {
      decayCoef = 0.0;
      decayBase = sustainLevel;
    }
    return timeSeconds;
  }

  public double sustainLevel(double level) {
    this.sustainLevel = Math.max(0.0, Math.min(1.0, level));
    // Recalculate decay base because it depends on sustain level
    decayBase = (this.sustainLevel - TARGET_RATIO_DR) * (1.0 - decayCoef);
    return this.sustainLevel;
  }

  public double releaseTime(double timeSeconds) {
    double timeInSamples = timeSeconds * sampleRate;
    this.releaseRate = timeInSamples;
    if (timeInSamples > 0.0) {
      releaseCoef = Math.exp(-Math.log((1.0 + TARGET_RATIO_DR) / TARGET_RATIO_DR) / timeInSamples);
      releaseBase = -TARGET_RATIO_DR * (1.0 - releaseCoef);
    } else {
      releaseCoef = 0.0;
      releaseBase = 0.0;
    }
    return timeSeconds;
  }

  public double fastReleaseTime(double timeSeconds) {
    double timeInSamples = timeSeconds * sampleRate;
    this.fastReleaseRate = timeInSamples;
    if (timeInSamples > 0.0) {
      fastReleaseCoef =
          Math.exp(-Math.log((1.0 + TARGET_RATIO_DR) / TARGET_RATIO_DR) / timeInSamples);
      fastReleaseBase = -TARGET_RATIO_DR * (1.0 - fastReleaseCoef);
    } else {
      fastReleaseCoef = 0.0;
      fastReleaseBase = 0.0;
    }
    return timeSeconds;
  }

  public void set(double a, double d, double s, double r) {
    attackTime(a);
    decayTime(d);
    sustainLevel(s);
    releaseTime(r);
  }

  public int keyOn() {
    target = 1.0;
    state = ATTACK;
    return 1;
  }

  public int keyOn(int ignored) {
    return keyOn();
  }

  public void keyOff() {
    target = 0.0;
    state = RELEASE;
  }

  public int keyOff(int ignored) {
    keyOff();
    return 1;
  }

  public void forceMute() {
    state = IDLE;
    value = 0.0;
    target = 0.0;
  }

  public int forceMute(int ignored) {
    forceMute();
    return 1;
  }

  public int fastRelease() {
    target = 0.0;
    state = FAST_RELEASE;
    return 1;
  }

  public int state() {
    return state;
  }

  public double value() {
    return value;
  }

  @Override
  protected float compute(float input, long systemTime) {
    switch (state) {
      case IDLE:
        value = 0.0;
        break;
      case ATTACK:
        value = attackBase + value * attackCoef;
        if (value >= 0.9999) {
          value = 1.0;
          target = sustainLevel;
          state = DECAY;
        }
        break;
      case DECAY:
        value = decayBase + value * decayCoef;
        if (value <= sustainLevel) {
          value = sustainLevel;
          state = SUSTAIN;
        }
        break;
      case SUSTAIN:
        value = sustainLevel;
        break;
      case RELEASE:
        value = releaseBase + value * releaseCoef;
        if (value <= 0.0) {
          value = 0.0;
          state = IDLE;
        }
        break;
      case FAST_RELEASE:
        value = fastReleaseBase + value * fastReleaseCoef;
        if (value <= 0.0) {
          value = 0.0;
          state = IDLE;
        }
        break;
    }

    // Apply envelope to input
    float out = (float) (input * value);
    if (Math.abs(out) < 1.0e-15f) out = 0.0f;
    return out;
  }
}
