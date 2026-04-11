package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;

/**
 * ModalBar — modal-resonance bar physical model. Models a struck bar (xylophone, vibraphone,
 * marimba) using a superposition of exponentially-decaying sinusoidal modes.
 *
 * <p>The first 4 modes follow a typical bar inharmonic series. Mode ratios: 1.0, 3.984, 10.00,
 * 19.33 (marimba default).
 */
public class ModalBar extends ChuckUGen {
  private static final int MODES = 4;

  // Inharmonic mode frequency ratios for a free bar
  private static final double[] MODE_RATIO = {1.0, 3.984, 10.00, 19.33};
  // Relative gain per mode
  private static final double[] MODE_GAIN = {1.0, 0.7, 0.5, 0.3};
  // Decay per mode (seconds): lower modes sustain longer
  private static final double[] MODE_DECAY = {0.9, 0.5, 0.3, 0.2};

  private final double[] phase = new double[MODES];
  private final double[] decayEn = new double[MODES]; // current envelope
  private final double[] phaseInc = new double[MODES];
  private final double[] decayCoef = new double[MODES];

  private double freq = 440.0;
  private final float sampleRate;

  public ModalBar(float sr) {
    this.sampleRate = sr;
    setFreq(440.0);
  }

  public void setFreq(double f) {
    freq = f;
    for (int m = 0; m < MODES; m++) {
      phaseInc[m] = 2.0 * Math.PI * f * MODE_RATIO[m] / sampleRate;
      decayCoef[m] = Math.exp(-1.0 / (MODE_DECAY[m] * sampleRate));
    }
  }

  public void noteOn(float velocity) {
    for (int m = 0; m < MODES; m++) {
      decayEn[m] = velocity * (float) MODE_GAIN[m];
      phase[m] = 0.0;
    }
  }

  public void noteOff(float velocity) {
    // Fast mute
    for (int m = 0; m < MODES; m++) decayEn[m] *= 0.01f;
  }

  @Override
  protected float compute(float input, long systemTime) {
    float out = 0;
    for (int m = 0; m < MODES; m++) {
      out += (float) (Math.sin(phase[m]) * decayEn[m]);
      phase[m] += phaseInc[m];
      decayEn[m] *= decayCoef[m];
    }
    out *= gain;
    lastOut = out;
    return out;
  }
}
