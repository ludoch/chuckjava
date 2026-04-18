package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.osc.SinOsc;
import org.chuck.audio.util.Adsr;

/**
 * HevyMetl — Heavy metal distorted FM synthesis. 4-operator FM with high modulation index for
 * metallic, distorted timbre.
 */
public class HevyMetl extends ChuckUGen {
  private final SinOsc carrier1;
  private final SinOsc carrier2;
  private final SinOsc mod1;
  private final SinOsc mod2;
  private final Adsr env1;
  private final Adsr env2;
  private double baseFreq = 440.0;

  @SuppressWarnings("unused")
  private final float sampleRate;

  public HevyMetl(float sampleRate) {
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

    // Fast attack, medium decay — punchy metal sound
    env1.set(0.001f, 0.5f, 0.2f, 0.1f);
    env2.set(0.001f, 0.2f, 0.0f, 0.05f);
  }

  public void setFreq(double freq) {
    this.baseFreq = freq;
    carrier1.setFreq(freq);
    carrier2.setFreq(freq * 1.005); // slight detune for thickness
    mod1.setFreq(freq * 3.0); // high ratio modulator
    mod2.setFreq(freq * 1.4); // inharmonic modulator
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
    float out = (c1 * e1 + c2 * e2 * 0.5f) * gain;
    return out;
  }
}
