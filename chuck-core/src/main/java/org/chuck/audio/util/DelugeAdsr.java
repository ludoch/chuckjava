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

  private int state = IDLE;
  private float value = 0.0f;
  private float target = 0.0f;

  // Durations in samples
  private float attackRate = 0.0f;
  private float decayRate = 0.0f;
  private float releaseRate = 0.0f;
  private float fastReleaseRate = 0.0f;

  private float sustainLevel = 0.7f;

  // Coefficients for exponential filters
  private float attackCoef = 0.0f;
  private float decayCoef = 0.0f;
  private float releaseCoef = 0.0f;
  private float fastReleaseCoef = 0.0f;

  // Base offset for attack to ensure it reaches 1.0 in finite time
  private static final float TARGET_RATIO_A = 0.3f;
  private static final float TARGET_RATIO_DR = 0.0001f;

  private float attackBase = 0.0f;
  private float decayBase = 0.0f;
  private float releaseBase = 0.0f;
  private float fastReleaseBase = 0.0f;

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
    float timeInSamples = (float) (timeSeconds * sampleRate);
    this.attackRate = timeInSamples;
    if (timeInSamples > 0.0f) {
      attackCoef =
          (float) Math.exp(-Math.log((1.0 + TARGET_RATIO_A) / TARGET_RATIO_A) / timeInSamples);
      attackBase = (1.0f + TARGET_RATIO_A) * (1.0f - attackCoef);
    } else {
      attackCoef = 0.0f;
      attackBase = 1.0f;
    }
    return timeSeconds;
  }

  public double decayTime(double timeSeconds) {
    float timeInSamples = (float) (timeSeconds * sampleRate);
    this.decayRate = timeInSamples;
    if (timeInSamples > 0.0f) {
      decayCoef =
          (float) Math.exp(-Math.log((1.0 + TARGET_RATIO_DR) / TARGET_RATIO_DR) / timeInSamples);
      decayBase = (sustainLevel - TARGET_RATIO_DR) * (1.0f - decayCoef);
    } else {
      decayCoef = 0.0f;
      decayBase = sustainLevel;
    }
    return timeSeconds;
  }

  public double sustainLevel(double level) {
    this.sustainLevel = (float) Math.max(0.0, Math.min(1.0, level));
    // Recalculate decay base because it depends on sustain level
    decayBase = (this.sustainLevel - TARGET_RATIO_DR) * (1.0f - decayCoef);
    return this.sustainLevel;
  }

  public double releaseTime(double timeSeconds) {
    float timeInSamples = (float) (timeSeconds * sampleRate);
    this.releaseRate = timeInSamples;
    if (timeInSamples > 0.0f) {
      releaseCoef =
          (float) Math.exp(-Math.log((1.0 + TARGET_RATIO_DR) / TARGET_RATIO_DR) / timeInSamples);
      releaseBase = -TARGET_RATIO_DR * (1.0f - releaseCoef);
    } else {
      releaseCoef = 0.0f;
      releaseBase = 0.0f;
    }
    return timeSeconds;
  }

  public double fastReleaseTime(double timeSeconds) {
    float timeInSamples = (float) (timeSeconds * sampleRate);
    this.fastReleaseRate = timeInSamples;
    if (timeInSamples > 0.0f) {
      fastReleaseCoef =
          (float) Math.exp(-Math.log((1.0 + TARGET_RATIO_DR) / TARGET_RATIO_DR) / timeInSamples);
      fastReleaseBase = -TARGET_RATIO_DR * (1.0f - fastReleaseCoef);
    } else {
      fastReleaseCoef = 0.0f;
      fastReleaseBase = 0.0f;
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
    target = 1.0f;
    state = ATTACK;
    return 1;
  }

  public int keyOn(int ignored) {
    return keyOn();
  }

  public int keyOff() {
    target = 0.0f;
    state = RELEASE;
    return 1;
  }

  public int keyOff(int ignored) {
    return keyOff();
  }

  public int fastRelease() {
    target = 0.0f;
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
        break;
      case ATTACK:
        value = attackBase + value * attackCoef;
        if (value >= 0.9999f) {
          value = 1.0f;
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
        if (value <= 0.0f) {
          value = 0.0f;
          state = IDLE;
        }
        break;
      case FAST_RELEASE:
        value = fastReleaseBase + value * fastReleaseCoef;
        if (value <= 0.0f) {
          value = 0.0f;
          state = IDLE;
        }
        break;
    }

    // Apply envelope to input
    return input * value;
  }
}
