package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.osc.SinOsc;
import org.chuck.audio.util.Adsr;

/**
 * KrstlChr — FM crystal choir. Four detuned FM voice pairs produce a crystalline, choir-like
 * texture. Each pair uses a high modulation index for the ethereal quality.
 */
public class KrstlChr extends ChuckUGen {
  private static final int VOICES = 4;
  private final SinOsc[] mods = new SinOsc[VOICES];
  private final SinOsc[] cars = new SinOsc[VOICES];
  private final Adsr env;
  private double freq = 220.0;
  private double modIndex = 3.5;
  // Slight detuning per voice (in cents as ratio)
  private static final double[] DETUNE = {1.000, 1.0007, 0.9993, 1.0014};
  // FM ratios relative to fundamental
  private static final double[] MOD_RATIO = {1.0, 2.0, 3.0, 0.5};
  private final float sampleRate;

  public KrstlChr(float sr) {
    this.sampleRate = sr;
    for (int i = 0; i < VOICES; i++) {
      mods[i] = new SinOsc(sr);
      cars[i] = new SinOsc(sr);
    }
    env = new Adsr(sr);
    env.set(0.2f, 0.1f, 0.8f, 0.3f); // slow fade in/out
    setFreq(220.0);
  }

  public void setFreq(double f) {
    freq = f;
    for (int i = 0; i < VOICES; i++) {
      mods[i].setFreq(f * MOD_RATIO[i] * DETUNE[i]);
      cars[i].setFreq(f * DETUNE[i]);
    }
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
    float mix = 0;
    for (int i = 0; i < VOICES; i++) {
      double modSig = mods[i].tick(t) * modIndex;
      cars[i].setFreq(freq * DETUNE[i] + modSig * freq * DETUNE[i]);
      mix += cars[i].tick(t);
    }
    float out = mix * (1.0f / VOICES) * e * gain;
    lastOut = out;
    return out;
  }
}
