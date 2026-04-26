package org.chuck.audio.osc;

/** A square wave oscillator. Matches native ChucK (subclass of PulseOsc with width=0.5). */
public class SqrOsc extends PulseOsc {
  public SqrOsc() {
    super();
    this.width = 0.5;
  }

  public SqrOsc(float sampleRate) {
    super(sampleRate);
    this.width = 0.5;
  }

  @Override
  public double width(double w) {
    this.width = 0.5;
    return 0.5;
  }

  @Override
  public void setWidth(double w) {
    this.width = 0.5;
  }
}
