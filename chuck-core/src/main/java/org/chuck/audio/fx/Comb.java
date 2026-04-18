package org.chuck.audio.fx;

import org.chuck.audio.ChuckUGen;

/** A comb filter UGen. Adapted from STK. */
public class Comb extends ChuckUGen {
  private final Delay delayLine;
  private float coefficient = 0.7f;

  public Comb(int delaySamples) {
    this(delaySamples, true);
  }

  public Comb(int delaySamples, boolean autoRegister) {
    super(autoRegister);
    this.delayLine = new Delay(delaySamples, 44100.0f, false);
    this.delayLine.delay(delaySamples);
  }

  public void delay(double samples) {
    delayLine.delay(samples);
  }

  public void setCoefficient(float c) {
    this.coefficient = c;
  }

  @Override
  protected float compute(float input, long systemTime) {
    float temp = delayLine.getLastOut();
    float out = input + coefficient * temp;
    delayLine.tick(out, systemTime);
    return temp;
  }
}
