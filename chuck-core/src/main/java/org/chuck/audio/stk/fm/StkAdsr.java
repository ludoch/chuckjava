package org.chuck.audio.stk.fm;

/**
 * Faithful port of STK's ADSR (ugen_stk.cpp), used internally by {@link FmInstrument} operators.
 * Plain DSP object ticked directly by the owning instrument. Rates are derived from times exactly
 * as ChucK's ADSR: attack 1/(t·sr), decay (1−sustain)/(t·sr), release sustain/(t·sr).
 */
public final class StkAdsr {
  private static final int ATTACK = 0, DECAY = 1, SUSTAIN = 2, RELEASE = 3, DONE = 4;

  private final double sampleRate;
  private int state = DONE;
  private double value = 0.0;
  private double target = 0.0;
  private double rate = 0.001;

  private double attackRate = 0.001;
  private double decayRate = 0.001;
  private double releaseRate = 0.005;
  private double sustainLevel = 0.5;

  private double attackTime = -1.0;
  private double decayTime = -1.0;
  private double releaseTime = -1.0;

  public StkAdsr(double sampleRate) {
    this.sampleRate = sampleRate;
  }

  public void setAllTimes(double aTime, double dTime, double sLevel, double rTime) {
    setSustainLevel(sLevel); // first: decay/release rates depend on sustain level
    setAttackTime(aTime);
    setDecayTime(dTime);
    setReleaseTime(rTime);
  }

  public void setSustainLevel(double level) {
    sustainLevel = Math.max(0.0, level);
    if (decayTime > 0.0) setDecayTime(decayTime);
    if (releaseTime > 0.0) setReleaseTime(releaseTime);
  }

  public void setAttackTime(double t) {
    attackRate = 1.0 / (Math.abs(t) * sampleRate);
    attackTime = t;
  }

  public void setDecayTime(double t) {
    if (t < 0.0) t = -t;
    decayRate = (t == 0.0) ? Float.MAX_VALUE : (1.0 - sustainLevel) / (t * sampleRate);
    decayTime = t;
  }

  public void setReleaseTime(double t) {
    if (t < 0.0) t = -t;
    releaseRate = (t == 0.0) ? Float.MAX_VALUE : sustainLevel / (t * sampleRate);
    releaseTime = t;
  }

  public void keyOn() {
    target = 1.0;
    rate = attackRate;
    state = ATTACK;
  }

  public void keyOff() {
    if (releaseTime > 0.0) rate = value / (releaseTime * sampleRate);
    else rate = releaseRate;
    target = 0.0;
    state = RELEASE;
  }

  public double tick() {
    switch (state) {
      case ATTACK -> {
        value += rate;
        if (attackTime <= 0 || value >= target) {
          state = DECAY;
          value = target;
          target = sustainLevel;
          rate = decayRate;
        }
      }
      case DECAY -> {
        value -= rate;
        if (decayTime <= 0 || value <= sustainLevel) {
          state = SUSTAIN;
          value = sustainLevel;
          rate = 0.0;
        }
      }
      case RELEASE -> {
        value -= rate;
        if (releaseTime <= 0 || value <= 0.0) {
          state = DONE;
          value = 0.0;
        }
      }
      default -> {
        /* SUSTAIN / DONE: hold */
      }
    }
    return value;
  }
}
