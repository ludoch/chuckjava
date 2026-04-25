package org.chuck.audio.osc;

/**
 * Sawtooth wave oscillator. To match native ChucK, this is implemented using TriOsc formula with
 * width=0.0 (falling) or width=1.0 (rising). By default it is rising (width=1.0) in native ChucK.
 */
public class SawOsc extends TriOsc {
  public SawOsc() {
    super();
    this.width = 1.0;
  }

  public SawOsc(float sampleRate) {
    super(sampleRate);
    this.width = 1.0;
  }

  @Override
  public double width(double w) {
    this.width = (w < 0.5) ? 0.0 : 1.0;
    return this.width;
  }

  @Override
  public void setWidth(double w) {
    this.width = (w < 0.5) ? 0.0 : 1.0;
  }
}
