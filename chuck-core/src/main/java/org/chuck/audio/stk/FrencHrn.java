package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.osc.SinOsc;
import org.chuck.audio.util.Adsr;

/**
 * FrencHrn — FM French horn. Uses two-operator FM with a ratio of ~1:1 and slow attack, with an
 * additional even harmonic for the horn's warmth.
 */
public class FrencHrn extends ChuckUGen {
  private final SinOsc mod, car, sub;
  private final Adsr env;
  private double freq = 220.0;
  private double modIndex = 1.8;
  private final float sampleRate;

  public FrencHrn(float sr) {
    this.sampleRate = sr;
    mod = new SinOsc(sr);
    car = new SinOsc(sr);
    sub = new SinOsc(sr);
    env = new Adsr(sr);
    env.set(0.12f, 0.08f, 0.85f, 0.18f); // slow horn-like attack
    setFreq(220.0);
  }

  public void setFreq(double f) {
    freq = f;
    mod.setFreq(f);
    car.setFreq(f);
    sub.setFreq(f * 0.5); // sub-octave for warmth
  }

  public void noteOn(float v) {
    env.keyOn();
  }

  public void noteOff(float v) {
    env.keyOff();
  }

  @Override
  protected float compute(float input, long t) {
    float e = env.tick(t);
    double modSig = mod.tick(t) * modIndex;
    car.setFreq(freq + modSig * freq);
    float out = (car.tick(t) * 0.7f + sub.tick(t) * 0.3f) * e * gain;
    lastOut = out;
    return out;
  }
}
