package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.osc.SinOsc;
import org.chuck.audio.util.Adsr;

/**
 * PercFlut — Percussive flute using 4-operator FM synthesis. Fast decay with low FM index produces
 * a breathy, flute-like tone.
 */
public class PercFlut extends ChuckUGen {
  private final SinOsc carrier1;
  private final SinOsc carrier2;
  private final SinOsc mod1;
  private final SinOsc mod2;
  private final Adsr env1;
  private final Adsr env2;
  private double baseFreq = 440.0;

  @SuppressWarnings("unused")
  private final float sampleRate;

  public PercFlut(float sampleRate) {
    this.sampleRate = sampleRate;
    this.carrier1 = new SinOsc(sampleRate);
    this.carrier2 = new SinOsc(sampleRate);
    this.mod1 = new SinOsc(sampleRate);
    this.mod2 = new SinOsc(sampleRate);
    this.env1 = new Adsr(sampleRate);
    this.env2 = new Adsr(sampleRate);

    mod1.chuckTo(carrier1);
    mod2.chuckTo(carrier2);
    carrier1.setSync(2);
    carrier2.setSync(2);

    // Fast attack, fast decay — percussive character
    env1.set(0.001f, 0.3f, 0.0f, 0.05f);
    env2.set(0.001f, 0.15f, 0.0f, 0.05f);
  }

  public void setFreq(double freq) {
    this.baseFreq = freq;
    carrier1.setFreq(freq);
    carrier2.setFreq(freq * 1.003); // very slight detune
    mod1.setFreq(freq * 1.0); // near-unison mod: breathy
    mod2.setFreq(freq * 2.0); // octave modulator
  }

  public void noteOn(float velocity) {
    env1.keyOn();
    env2.keyOn();
  }

  public void noteOff(float velocity) {
    env1.keyOff();
    env2.keyOff();
  }

  @Override
  protected float compute(float input, long systemTime) {
    float e1 = env1.tick(systemTime);
    float e2 = env2.tick(systemTime);
    float c1 = carrier1.tick(systemTime);
    float c2 = carrier2.tick(systemTime);
    @SuppressWarnings("unused")
    float m1 = mod1.tick(systemTime);
    @SuppressWarnings("unused")
    float m2 = mod2.tick(systemTime);
    float out = (c1 * e1 + c2 * e2 * 0.4f) * gain;
    return out;
  }
}
