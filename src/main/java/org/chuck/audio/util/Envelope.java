package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;

/** A simple linear envelope UGen. */
public class Envelope extends ChuckUGen {
  private float target = 0.0f;
  private float value = 0.0f;
  private float rate = 0.001f;
  private final float sampleRate;

  public Envelope(float sampleRate) {
    this(sampleRate, true);
  }

  public Envelope(float sampleRate, boolean autoRegister) {
    super(autoRegister);
    this.sampleRate = sampleRate;
  }

  public void setTarget(float target) {
    this.target = target;
  }

  public void setRate(float rate) {
    this.rate = rate;
  }

  public void setTime(float seconds) {
    if (seconds <= 0) {
      this.rate = 1.0f;
    } else {
      this.rate = 1.0f / (seconds * sampleRate);
    }
  }

  public void setDuration(long samples) {
    if (samples <= 0) {
      this.rate = 1.0f;
    } else {
      this.rate = 1.0f / samples;
    }
  }

  public void setValue(float value) {
    this.value = value;
    this.target = value;
  }

  public void keyOn() {
    this.target = 1.0f;
  }

  public void setKeyOn(double val) {
    if (val > 0) keyOn();
  }

  public void keyOff() {
    this.target = 0.0f;
  }

  public void setKeyOff(double val) {
    if (val > 0) keyOff();
  }

  @Override
  protected float compute(float input, long systemTime) {
    if (value < target) {
      value += rate;
      if (value > target) value = target;
    } else if (value > target) {
      value -= rate;
      if (value < target) value = target;
    }
    return input * value;
  }

  public float getValue() {
    return value;
  }
}
