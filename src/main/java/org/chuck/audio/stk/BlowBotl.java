package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.filter.OnePole;
import org.chuck.audio.fx.DelayL;
import org.chuck.audio.osc.Noise;
import org.chuck.audio.util.Adsr;

/**
 * BlowBotl — Blown bottle physical model. Breath noise drives a resonant cavity (single-pole tube
 * resonance) with non-linear jet feedback, producing a breathy bottle sound.
 */
public class BlowBotl extends ChuckUGen {
  private final DelayL tube;
  private final OnePole filter;
  private final Noise noise;
  private final Adsr env;
  private float noiseGain = 0.5f;
  private float endReflection = -0.5f;
  private float pressure = 0.4f;
  private final float sampleRate;
  private double freq = 220.0;

  public BlowBotl(float sr) {
    this.sampleRate = sr;
    tube = new DelayL((int) (sr * 0.05));
    filter = new OnePole();
    filter.setPole(0.7f);
    noise = new Noise();
    env = new Adsr(sr);
    env.set(0.01f, 0.05f, 0.8f, 0.1f);
    setFreq(220.0);
  }

  public void setFreq(double f) {
    freq = f;
    double delay = sampleRate / f / 2.0 - 2.0;
    tube.setDelay(Math.max(1.0, delay));
  }

  public void noteOn(float v) {
    pressure = 0.1f + v * 0.9f;
    env.keyOn();
  }

  public void noteOff(float v) {
    env.keyOff();
  }

  @Override
  protected float compute(float input, long t) {
    float e = env.tick(t);
    float breath = pressure * e + noise.tick(t) * noiseGain * e;
    float tubeOut = tube.getLastOut();
    float jetInput = breath + tubeOut * endReflection;
    // Non-linear jet clipping
    jetInput = Math.max(-1.0f, Math.min(1.0f, jetInput));
    float filtered = filter.tick(jetInput, t);
    tube.tick(filtered, t);
    float out = tubeOut * gain;
    lastOut = out;
    return out;
  }
}
