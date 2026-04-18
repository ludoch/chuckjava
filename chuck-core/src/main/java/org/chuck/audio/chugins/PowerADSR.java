package org.chuck.audio.chugins;

import org.chuck.audio.ChuckUGen;

/**
 * PowerADSR — ADSR envelope with power-shaped stages.
 *
 * <p>Each stage (attack, decay, release) can have an independent power curve applied to its linear
 * ramp, allowing convex/concave shaping. curve = 1.0 is linear; &lt; 1.0 is convex (fast rise, slow
 * tail); &gt; 1.0 is concave (slow rise, fast peak).
 *
 * <p>Port of chugins/PowerADSR by Eric Heep.
 */
public class PowerADSR extends ChuckUGen {
  public static final int ATTACK = 0;
  public static final int DECAY = 1;
  public static final int SUSTAIN = 2;
  public static final int RELEASE = 3;
  public static final int DONE = 4;

  private final float sampleRate;

  private double attackDuration;
  private double decayDuration;
  private double releaseDuration;
  private double sustainLevel = 0.5;

  private double inverseAttackDuration;
  private double inverseDecayDuration;
  private double inverseReleaseDuration;

  private double linearValue = 0.0;
  private double powerValue = 0.0;
  private double offsetValue = 0.0;
  private double scalarValue = 1.0;
  private double value = 0.0;
  private double target = 1.0;

  private double sampleCount = 0.0;

  private double attackCurve = 1.0;
  private double decayCurve = 1.0;
  private double releaseCurve = 1.0;

  private double curve = 1.0;
  private double inverseDuration = 0.0;

  private int state = DONE;

  public PowerADSR(float sampleRate) {
    this.sampleRate = sampleRate;
    attackDuration = sampleRate;
    decayDuration = sampleRate;
    releaseDuration = sampleRate;
    inverseAttackDuration = 1.0 / sampleRate;
    inverseDecayDuration = 1.0 / sampleRate;
    inverseReleaseDuration = 1.0 / sampleRate;
  }

  @Override
  protected float compute(float input, long systemTime) {
    switch (state) {
      case ATTACK -> {
        sampleCount++;
        if (sampleCount >= attackDuration) {
          sampleCount = decayDuration;
          inverseDuration = inverseDecayDuration;
          curve = decayCurve;
          offsetValue = sustainLevel;
          scalarValue = target - sustainLevel;
          state = DECAY;
        }
      }

      case DECAY -> {
        sampleCount--;
        if (sampleCount <= 0.0) {
          sampleCount = releaseDuration;
          inverseDuration = inverseReleaseDuration;
          curve = releaseCurve;
          offsetValue = 0.0;
          scalarValue = sustainLevel;
          state = SUSTAIN;
        }
      }

      case SUSTAIN -> {}

      case RELEASE -> {
        sampleCount--;
        if (sampleCount <= 0.0) {
          linearValue = 0.0;
          scalarValue = 1.0;
          state = DONE;
        }
      }

      case DONE -> {}
    }

    linearValue = sampleCount * inverseDuration;
    powerValue = Math.pow(linearValue, curve);
    value = powerValue * scalarValue + offsetValue;

    return (float) (input * value);
  }

  public int keyOn() {
    sampleCount = 0.0;
    inverseDuration = inverseAttackDuration;
    curve = attackCurve;
    offsetValue = value;
    scalarValue = target - value;
    state = ATTACK;
    return 1;
  }

  public int keyOn(int ignored) {
    return keyOn();
  }

  public int keyOff() {
    sampleCount = releaseDuration;
    inverseDuration = inverseReleaseDuration;
    curve = releaseCurve;
    offsetValue = 0.0;
    scalarValue = value;
    state = RELEASE;
    return 1;
  }

  public int keyOff(int ignored) {
    return keyOff();
  }

  public double attackTime(double samps) {
    attackDuration = Math.max(1.0, samps);
    inverseAttackDuration = 1.0 / attackDuration;
    return samps;
  }

  public double attackTime() {
    return attackDuration;
  }

  public double decayTime(double samps) {
    decayDuration = Math.max(1.0, samps);
    inverseDecayDuration = 1.0 / decayDuration;
    return samps;
  }

  public double decayTime() {
    return decayDuration;
  }

  public double sustainLevel(double level) {
    sustainLevel = Math.max(0.0, Math.min(1.0, level));
    return sustainLevel;
  }

  public double sustainLevel() {
    return sustainLevel;
  }

  public double releaseTime(double samps) {
    releaseDuration = Math.max(1.0, samps);
    inverseReleaseDuration = 1.0 / releaseDuration;
    return samps;
  }

  public double releaseTime() {
    return releaseDuration;
  }

  public void set(double aDur, double dDur, double sLvl, double rDur) {
    attackTime(aDur);
    decayTime(dDur);
    sustainLevel(sLvl);
    releaseTime(rDur);
  }

  public double attackCurve(double c) {
    attackCurve = c;
    return c;
  }

  public double attackCurve() {
    return attackCurve;
  }

  public double decayCurve(double c) {
    decayCurve = c;
    return c;
  }

  public double decayCurve() {
    return decayCurve;
  }

  public double releaseCurve(double c) {
    releaseCurve = c;
    return c;
  }

  public double releaseCurve() {
    return releaseCurve;
  }

  public void setCurves(double a, double d, double r) {
    attackCurve = a;
    decayCurve = d;
    releaseCurve = r;
  }

  public double value() {
    return value;
  }

  public int state() {
    return state;
  }
}
