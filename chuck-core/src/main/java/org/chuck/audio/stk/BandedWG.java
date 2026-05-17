package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.filter.OnePole;
import org.chuck.audio.fx.DelayL;
import org.chuck.audio.osc.Noise;

/**
 * BandedWG — Banded Waveguide model (bowed bar, tibetan bowl). Uses a set of waveguide resonators
 * at inharmonic mode frequencies, excited by a noise burst and sustained by bow-friction feedback.
 */
public class BandedWG extends ChuckUGen {
  private static final int BANDS = 4;
  // Tibetan bowl mode ratios
  private static final double[] MODE_RATIO = {1.0, 2.756, 5.404, 8.933};
  private static final double[] MODE_GAIN = {1.0, 0.6, 0.4, 0.2};

  private final DelayL[] delays = new DelayL[BANDS];
  private final OnePole[] filters = new OnePole[BANDS];
  private final double[] decayCoef = new double[BANDS];
  private final double[] envLevel = new double[BANDS];

  private final Noise noise = new Noise();
  private double freq = 110.0;
  private boolean bowing = false;
  private float bowVelocity = 0.5f;
  private final float sampleRate;

  public BandedWG(float sr) {
    this.sampleRate = sr;
    for (int i = 0; i < BANDS; i++) {
      delays[i] = new DelayL((int) (sr * 0.05));
      filters[i] = new OnePole();
      filters[i].setPole(0.9f);
    }
    setFreq(110.0);
  }

  public void setFreq(double f) {
    freq = f;
    for (int i = 0; i < BANDS; i++) {
      double modeFreq = f * MODE_RATIO[i];
      double delaySamples = sampleRate / modeFreq;
      delays[i].setDelay(delaySamples);
      decayCoef[i] = Math.exp(-1.0 / (0.3 * sampleRate / modeFreq));
    }
  }

  public void noteOn(float velocity) {
    for (int i = 0; i < BANDS; i++) {
      envLevel[i] = velocity * MODE_GAIN[i];
    }
    bowing = true;
    bowVelocity = velocity;
  }

  public void noteOff(float velocity) {
    bowing = false;
  }

  @Override
  protected float compute(float input, long t) {
    float out = 0;
    float excite = bowing ? noise.tick(t) * bowVelocity * 0.1f : 0;

    for (int i = 0; i < BANDS; i++) {
      float delayed = delays[i].getLastOut();
      float filtered = filters[i].tick(delayed + excite, t);
      delays[i].tick(filtered, t);
      envLevel[i] *= decayCoef[i];
      out += (float) (filtered * envLevel[i] * MODE_GAIN[i]);
    }
    out *= gain;
    lastOut = out;
    return out;
  }
}
