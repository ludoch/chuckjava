package org.chuck.audio.osc;

/** A square wave oscillator. */
public class SqrOsc extends PulseOsc {
  public SqrOsc(float sampleRate) {
    super(sampleRate);
    this.width = 0.5;
  }
}
