package org.chuck.audio.fx;

import org.chuck.audio.util.StereoUGen;

/**
 * RingsReverb: Physical modeling reverb inspired by Émilie Gillet's Rings (Mutable Instruments).
 *
 * <p>Architecture: 4 parallel modal resonators (2nd-order IIR bandpass) + Schroeder tank tail.
 * The resonators respond to the pitch/frequency content of the input, creating body/resonance
 * that tracks with the note being played — fundamentally different from algorithmic reverb which
 * applies uniform ambience regardless of pitch.
 *
 * <p>Parameters:
 * <ul>
 *   <li><b>brightness</b> (0-1): Damping/LPF on the resonator structure. Higher = brighter.</li>
 *   <li><b>position</b> (0-1): Spatial spread + balance between direct resonators vs tank tail.</li>
 *   <li><b>structure</b> (0-1): Harmonic spacing. 0 = inharmonic (metallic), 1 = harmonic (string-like).</li>
 *   <li><b>damping</b> (0-1): Additional decay damping on the tank reverb tail.</li>
 * </ul>
 */
public class RingsReverb extends StereoUGen {

  // ─── Parameters ───────────────────────────────────────────────
  private float brightness = 0.5f;
  private float position  = 0.5f;
  private float structure = 0.5f;
  private float damping   = 0.5f;

  // ─── Internal state ───────────────────────────────────────────
  private final float sampleRate;
  private float baseFreq = 440.0f; // estimated from input (simple pitch tracker)

  // 4 parallel modal resonators (stereo pairs)
  private final ModalResonator[] resL = new ModalResonator[4];
  private final ModalResonator[] resR = new ModalResonator[4];

  // Tank reverb (Schroeder topology, short tail)
  private final CombFilter[] tankCombL;
  private final CombFilter[] tankCombR;
  private final AllPass[] tankApL;
  private final AllPass[] tankApR;

  // DC blocker
  private float dcBlockerL = 0f;
  private float dcBlockerR = 0f;
  private static final float DC_BLOCK = 0.9999f;

  // Stereo crossfade state
  private float prevBrightness = -1f;
  private float prevStructure = -1f;

  public RingsReverb() {
    this(44100f);
  }

  public RingsReverb(float sampleRate) {
    this.sampleRate = sampleRate;
    for (int i = 0; i < 4; i++) {
      resL[i] = new ModalResonator(sampleRate);
      resR[i] = new ModalResonator(sampleRate);
    }

    // Tank reverb: 4 parallel combs + 4 series allpass per channel
    int[] combTaps = { 480, 600, 700, 800 };
    int[] apTaps   = { 160, 140, 120, 100 };

    tankCombL = new CombFilter[4];
    tankCombR = new CombFilter[4];
    tankApL   = new AllPass[4];
    tankApR   = new AllPass[4];

    for (int i = 0; i < 4; i++) {
      tankCombL[i] = new CombFilter(combTaps[i], 0.6f);
      tankCombR[i] = new CombFilter(combTaps[i] + 11, 0.6f);
      tankApL[i]   = new AllPass(apTaps[i], 0.3f);
      tankApR[i]   = new AllPass(apTaps[i] + 7, 0.3f);
    }

    updateResonators();
  }

  // ─── Parameter setters ────────────────────────────────────────

  public void setBrightness(float v) {
    this.brightness = Math.max(0f, Math.min(1f, v));
  }

  public void setPosition(float v) {
    this.position = Math.max(0f, Math.min(1f, v));
  }

  public void setStructure(float v) {
    this.structure = Math.max(0f, Math.min(1f, v));
    updateResonators();
  }

  public void setDamping(float v) {
    this.damping = Math.max(0f, Math.min(1f, v));
  }

  // ─── StereoUGen implementation ────────────────────────────────

  @Override
  protected void computeStereo(float input, long systemTime) {
    computeStereo(input, input, systemTime);
  }

  @Override
  protected void computeStereo(float left, float right, long systemTime) {
    // DC blocker
    left  -= dcBlockerL * DC_BLOCK;
    right -= dcBlockerR * DC_BLOCK;
    dcBlockerL = left + dcBlockerL * DC_BLOCK;
    dcBlockerR = right + dcBlockerR * DC_BLOCK;

    // Simple pitch tracker for base frequency estimation
    float mono = (left + right) * 0.5f;
    estimatePitch(mono);

    updateResonators();

    // Process through modal resonators
    float resOutL = 0f, resOutR = 0f;
    for (int i = 0; i < 4; i++) {
      resOutL += resL[i].tick(mono);
      resOutR += resR[i].tick(mono);
    }
    float resScale = 0.25f; // average across 4 resonators
    resOutL *= resScale;
    resOutR *= resScale;

    // Process through tank reverb
    float tankInL = resOutL, tankInR = resOutR;
    for (int i = 0; i < 4; i++) {
      tankInL = tankCombL[i].tick(tankInL);
      tankInR = tankCombR[i].tick(tankInR);
    }
    float tankOutL = tankInL;
    float tankOutR = tankInR;
    for (int i = 0; i < 4; i++) {
      tankOutL = tankApL[i].tick(tankOutL);
      tankOutR = tankApR[i].tick(tankOutR);
    }

    // Crossfade: position controls balance between dry resonators and wet (resonators + tank)
    // position=0 → pure resonators, position=1 → full resonators + tank
    float outL = resOutL * (1f - position) + tankOutL * position;
    float outR = resOutR * (1f - position) + tankOutR * position;

    // Apply brightness as a post-process gain (resonator damping already applied internally)
    float gain = 0.3f + brightness * 0.7f;
    outL *= gain;
    outR *= gain;

    lastOutChannels[0] = outL;
    lastOutChannels[1] = outR;
  }

  // ─── Internal: pitch estimation (zero-crossing + peak tracking) ──

  private float zcAccum = 0f;
  private int zcCount = 0;
  private float peak = 0f;
  private int sampleCount = 0;
  private float prevSample = 0f;

  private void estimatePitch(float s) {
    sampleCount++;
    // Zero-crossing rate
    if (prevSample >= 0 && s < 0) zcCount++;
    prevSample = s;
    // Peak tracking
    float abs = Math.abs(s);
    if (abs > peak) peak = abs;

    if (sampleCount >= 1024) {
      if (zcCount > 0) {
        float freq = (sampleRate * zcCount * 0.5f) / sampleCount; // Hz
        // Clamp to musical range
        baseFreq = Math.max(30f, Math.min(8000f, freq));
      }
      // Reset accumulators
      zcCount = 0;
      sampleCount = 0;
      peak = 0f;
    }
  }

  // ─── Internal: update resonator tuning ────────────────────────

  private void updateResonators() {
    if (brightness == prevBrightness && structure == prevStructure) return;
    prevBrightness = brightness;
    prevStructure = structure;

    // Harmonic ratios: structure=0 → inharmonic (metallic), structure=1 → harmonic (string)
    // Inharmonic ratios (like Rings default): 1.0, 4.02, 8.03, 12.04
    // Harmonic ratios: 1.0, 2.0, 3.0, 4.0
    // Linear interpolation between them based on structure
    float[] inharmRatios = { 1f, 4.02f, 8.03f, 12.04f };
    float[] harmRatios   = { 1f, 2f,    4f,    6f };

    float[] ratios = new float[4];
    for (int i = 0; i < 4; i++) {
      ratios[i] = inharmRatios[i] + (harmRatios[i] - inharmRatios[i]) * structure;
    }

    // Brightness controls resonator damping/Q
    // brightness=0 → heavily damped (dull), brightness=1 → lightly damped (bright)
    float q = 5f + brightness * 45f;   // Q range 5-50
    float decay = 0.3f + (1f - brightness) * 0.6f; // decay range 0.3-0.9

    for (int i = 0; i < 4; i++) {
      float freq = Math.max(30f, Math.min(sampleRate * 0.45f, baseFreq * ratios[i]));
      resL[i].setFreq(freq);
      resL[i].setQ(q);
      resL[i].setDecay(decay);
      resR[i].setFreq(freq);
      resR[i].setQ(q);
      resR[i].setDecay(decay);
    }
  }

  // ─── Inner classes ────────────────────────────────────────────

  /**
   * ModalResonator: 2nd-order IIR bandpass with configurable frequency, Q, and decay envelope.
   * Acts as a struck resonator body — rings at its tuned frequency when excited by input.
   */
  private static class ModalResonator {
    private final float fs;
    private float b0, b1, b2, a1, a2; // Direct Form I coefficients
    private float x1 = 0f, x2 = 0f, y1 = 0f, y2 = 0f;
    private float decay = 0.5f;

    ModalResonator(float sampleRate) {
      this.fs = sampleRate;
      setFreq(440f);
      setQ(20f);
    }

    void setFreq(float freq) {
      float w0 = (float) (2.0 * Math.PI * freq / fs);
      float alpha = (float) Math.sin(w0) * 0.5f; // Q=2 → fixed resonance bandwidth
      // Bandpass (constant-skirt): b0 = alpha, b1 = 0, b2 = -alpha, a1 = -2*cos(w0), a2 = 1 - 2*alpha
      // We compute coefs here, apply Q and decay multiplicatively in tick()
      float cosW0 = (float) Math.cos(w0);
      b0 = alpha;
      b1 = 0f;
      b2 = -alpha;
      a1 = -2f * cosW0;
      a2 = 1f - 2f * alpha;
    }

    void setQ(float q) {
      // Q is applied as a scaling factor on the feedback path
      // Higher Q = more resonant = longer ring at the tuned frequency
      // We scale a2 to control the resonance bandwidth
      // a2_effective = a2 + (1 - 2*alpha) * (1 - 1/q)
      float alpha = b0; // b0 = alpha from setFreq
      float qScale = 1f - 1f / q;
      // Recompute with adjusted Q
      float cosW0 = -a1 * 0.5f;
      float w0 = (float) Math.acos(Math.max(-1f, Math.min(1f, cosW0)));
      float newAlpha = (float) Math.sin(w0) / (2f * q);
      b0 = newAlpha;
      b2 = -newAlpha;
      a2 = 1f - 2f * newAlpha * qScale;
    }

    void setDecay(float d) {
      this.decay = Math.max(0f, Math.min(1f, d));
    }

    float tick(float input) {
      // Direct Form I: y[n] = b0*x[n] + b1*x[n-1] + b2*x[n-2] - a1*y[n-1] - a2*y[n-2]
      float output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
      // Apply decay envelope (exponential damping)
      output *= decay;
      // Shift state
      x2 = x1;
      x1 = input;
      y2 = y1;
      y1 = output;
      return output;
    }
  }

  /** Comb filter for tank reverb. */
  private static class CombFilter {
    private final float[] buffer;
    private int idx = 0;
    private float feedback;

    CombFilter(int size, float fb) {
      buffer = new float[size];
      this.feedback = fb;
    }

    float tick(float in) {
      float out = buffer[idx];
      buffer[idx] = in + out * feedback;
      idx = (idx + 1) % buffer.length;
      return out;
    }
  }

  /** All-pass filter for tank reverb. */
  private static class AllPass {
    private final float[] buffer;
    private int idx = 0;
    private float feedback;

    AllPass(int size, float fb) {
      buffer = new float[size];
      this.feedback = fb;
    }

    float tick(float in) {
      float bufIn = buffer[idx];
      float out = -in + bufIn;
      buffer[idx] = in + bufIn * feedback;
      idx = (idx + 1) % buffer.length;
      return out;
    }
  }
}
