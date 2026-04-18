package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.osc.SinOsc;

/** Modulate: Vibrato/Tremolo modulation UGen. */
public class Modulate extends ChuckUGen {
  private final SinOsc vibrato;
  private float vibratoRate = 6.0f;
  private float vibratoGain = 0.0f;

  public Modulate(float sampleRate) {
    super();
    vibrato = new SinOsc(sampleRate);
    vibrato.setFreq(vibratoRate);
  }

  public void vibratoRate(float r) {
    this.vibratoRate = r;
    vibrato.setFreq(r);
  }

  public float vibratoRate() {
    return vibratoRate;
  }

  public void vibratoGain(float g) {
    this.vibratoGain = g;
  }

  public float vibratoGain() {
    return vibratoGain;
  }

  @Override
  protected float compute(float input, long systemTime) {
    // Simple vibrato: add sine to input
    return input + vibrato.tick(systemTime) * vibratoGain;
  }
}
