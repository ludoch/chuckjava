package org.chuck.audio.osc;

/** A square wave oscillator. */
public class SqrOsc extends Osc {
  public SqrOsc() {
    super();
  }

  public SqrOsc(float sampleRate) {
    super(sampleRate);
    this.width = 0.5;
  }

  @Override
  protected double computeOsc(double phase) {
    return phase < width ? 1.0 : -1.0;
  }
}
