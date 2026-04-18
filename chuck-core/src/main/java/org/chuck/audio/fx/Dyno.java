package org.chuck.audio.fx;

import org.chuck.audio.ChuckUGen;

/**
 * Dyno: Dynamics Processor (Compressor, Limiter, Expander, Noise Gate). Implemented with an
 * envelope follower and gain computer.
 */
public class Dyno extends ChuckUGen {
  // Modes
  public static final int COMPRESSOR = 0;
  public static final int LIMITER = 1;
  public static final int EXPANDER = 2;
  public static final int GATE = 3;
  public static final int DUCK = 4;

  private int mode = COMPRESSOR;
  private float thresh = 0.5f;
  private float ratio = 1.0f;
  private float slopeAbove = 1.0f;
  private float slopeBelow = 1.0f;
  private float attackTime = 0.005f; // in seconds
  private float releaseTime = 0.05f; // in seconds

  @SuppressWarnings("unused")
  private float knee = 0.0f;

  private float externalGain = 1.0f;

  private float envelope = 0.0f;
  private float sampleRate = 44100.0f;

  public Dyno() {
    super();
  }

  public Dyno(float sampleRate) {
    super();
    this.sampleRate = sampleRate;
  }

  @Override
  protected float compute(float input, long systemTime) {
    // Simple peak envelope follower
    float absIn = Math.abs(input);
    float target = absIn;

    float coeff;
    if (target > envelope) coeff = (float) Math.exp(-1.0 / (sampleRate * attackTime));
    else coeff = (float) Math.exp(-1.0 / (sampleRate * releaseTime));

    envelope = coeff * envelope + (1.0f - coeff) * target;

    float gainReduction = 1.0f;
    if (mode == COMPRESSOR || mode == LIMITER) {
      if (envelope > thresh) {
        float over = envelope / thresh;
        gainReduction = (float) Math.pow(over, (1.0 / ratio) - 1.0);
      }
    } else if (mode == EXPANDER || mode == GATE) {
      if (envelope < thresh) {
        float under = envelope / thresh;
        if (under < 1e-6) gainReduction = 0;
        else gainReduction = (float) Math.pow(under, slopeBelow - 1.0);
      }
    }

    return input * gainReduction * externalGain;
  }

  // --- Properties ---
  public void mode(int m) {
    this.mode = m;
  }

  public int mode() {
    return mode;
  }

  public void thresh(float v) {
    this.thresh = v;
  }

  public float thresh() {
    return thresh;
  }

  public void ratio(float v) {
    this.ratio = v;
  }

  public float ratio() {
    return ratio;
  }

  public void attackTime(double durSamples) {
    this.attackTime = (float) (durSamples / sampleRate);
  }

  public double attackTime() {
    return attackTime * sampleRate;
  }

  public void releaseTime(double durSamples) {
    this.releaseTime = (float) (durSamples / sampleRate);
  }

  public double releaseTime() {
    return releaseTime * sampleRate;
  }

  public void slopeAbove(float v) {
    this.slopeAbove = v;
  }

  public float slopeAbove() {
    return slopeAbove;
  }

  public void slopeBelow(float v) {
    this.slopeBelow = v;
  }

  public float slopeBelow() {
    return slopeBelow;
  }

  public void set(float thresh, float ratio, double attackSamples, double releaseSamples) {
    this.thresh = thresh;
    this.ratio = ratio;
    this.attackTime = (float) (attackSamples / sampleRate);
    this.releaseTime = (float) (releaseSamples / sampleRate);
  }

  public void compressor() {
    this.mode = COMPRESSOR;
  }

  public void limiter() {
    this.mode = LIMITER;
  }

  public void expander() {
    this.mode = EXPANDER;
  }

  public void gate() {
    this.mode = GATE;
  }

  public void duck() {
    this.mode = DUCK;
  }
}
