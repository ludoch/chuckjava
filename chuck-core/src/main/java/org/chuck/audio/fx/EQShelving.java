package org.chuck.audio.fx;

import org.chuck.audio.ChuckUGen;

/**
 * Shelving filter UGen matching the real Deluge firmware's EQ approach.
 *
 * <p>Based on {@code ModControllableAudio::doEQ()} from the firmware source (c1.3.0).
 * The real firmware uses <b>first-order one-pole IIR filters</b> — not biquads:
 * <ul>
 *   <li>LOW_SHELF (bass): 1-pole lowpass, output = input + lowpass_state * gain_mix</li>
 *   <li>HIGH_SHELF (treble): 1-pole lowpass → highpass = input - lowpass, output = input + highpass * gain_mix</li>
 * </ul>
 *
 * <p>Gain mixing follows the firmware's {@code ×8} scaling (left-shift 3):
 * {@code mix = (gain - 1.0) * 8.0}. At unity gain (1.0), mix = 0 → pure passthrough.
 *
 * <p>Linear gain range 0.0-2.0 (0 = -inf, 1.0 = 0dB bypass, 2.0 = +6dB).
 */
public class EQShelving extends ChuckUGen {

  public static final int LOW_SHELF = 0;
  public static final int HIGH_SHELF = 1;

  private int type = LOW_SHELF;
  private float freq = 200.0f;
  private float shelfGain = 1.0f; // linear amplitude gain
  private final float sampleRate;

  // 1-pole filter state (firmware uses double-precision state vars)
  private double stateL = 0.0; // lowpass state (mono/sum for mono compute)
  private boolean coeffsDirty = true;

  // Pre-computed per-sample coefficient
  private double poleCoeff;  // 1 - exp(-2*pi*f/sampleRate), firmware's "freq" parameter
  private double gainMix;    // (shelfGain - 1.0) * 8.0, firmware's ×8 scaling

  public EQShelving(float sampleRate) {
    super();
    this.sampleRate = sampleRate;
  }

  /** Set shelf type: LOW_SHELF (bass) or HIGH_SHELF (treble). */
  public void type(int t) {
    if (t != this.type) { this.type = t; } // type doesn't affect coeffs
  }

  /** Set corner frequency in Hz. */
  public void freq(float f) {
    if (f != this.freq) { this.freq = Math.max(10.0f, Math.min(sampleRate / 2.0f, f)); coeffsDirty = true; }
  }

  /** Set shelf gain (linear, 0.0-2.0). 1.0 = 0dB bypass. */
  public void shelfGain(float g) {
    if (g != this.shelfGain) { this.shelfGain = Math.max(0.0f, Math.min(2.0f, g)); coeffsDirty = true; }
  }

  /** Get shelf gain in dB. */
  public float gainDb() {
    return (float) (20.0 * Math.log10(Math.max(1e-6, shelfGain)));
  }

  /** Set shelf gain from dB (-24 to +6 dB). */
  public void gainDb(float db) {
    this.shelfGain = (float) Math.pow(10.0, Math.max(-24.0, Math.min(6.0, db)) / 20.0);
    coeffsDirty = true;
  }

  private void updateCoeffs() {
    // 1-pole LPF coefficient matching firmware:
    // state += (input - state) * poleCoeff
    // where poleCoeff = 1 - exp(-2*pi*f/fs)
    double norm = 2.0 * Math.PI * freq / sampleRate;
    poleCoeff = 1.0 - Math.exp(-norm);
    if (poleCoeff < 1e-10) poleCoeff = 1e-10;
    if (poleCoeff > 1.0) poleCoeff = 1.0;

    // Firmware multiplies gain amount by 8 (<< 3)
    // gain=1.0 → mix=0 (bypass), gain=0.5 → mix=-4 (cut), gain=1.5 → mix=+4 (boost)
    gainMix = (shelfGain - 1.0) * 8.0;

    // Reset state on coefficient change to avoid glitching
    stateL = 0.0;
    coeffsDirty = false;
  }

  @Override
  protected float compute(float input, long systemTime) {
    if (coeffsDirty) updateCoeffs();

    // 1-pole lowpass update: state += (input - state) * coeff
    double in = input;
    double dist = in - stateL;
    stateL += dist * poleCoeff;

    double out;
    if (type == LOW_SHELF) {
      // Bass: output = input + lowpass_state * gainMix
      double lpOut = stateL;
      out = in + lpOut * gainMix;
    } else {
      // Treble: highpass = input - lowpass, output = input + highpass * gainMix
      double hpOut = in - stateL;
      out = in + hpOut * gainMix;
    }

    return (float) out;
  }
}
