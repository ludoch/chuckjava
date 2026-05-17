package org.chuck.audio.fx;

import org.chuck.audio.util.StereoUGen;

/**
 * RingsReverb: Physical modeling reverb inspired by Émilie Gillet's Rings (Mutable Instruments).
 *
 * <p>Architecture: 4 parallel modal resonators (2nd-order IIR bandpass) + Schroeder tank tail, with
 * optional YIN autocorrelation pitch tracking, mallet excitation, and Karplus-Strong mode. The
 * resonators respond to the pitch/frequency content of the input, creating body/resonance that
 * tracks with the note being played — fundamentally different from algorithmic reverb which applies
 * uniform ambience regardless of pitch.
 *
 * <p>Parameters:
 *
 * <ul>
 *   <li><b>brightness</b> (0-1): Damping/LPF on the resonator structure. Higher = brighter.
 *   <li><b>position</b> (0-1): Spatial spread + balance between direct resonators vs tank tail.
 *   <li><b>structure</b> (0-1): Harmonic spacing. 0 = inharmonic (metallic), 1 = harmonic
 *       (string-like).
 *   <li><b>damping</b> (0-1): Additional decay damping on the tank reverb tail.
 *   <li><b>excitation</b> (0-1): Amount of mallet-style transient noise burst on attack detection.
 *   <li><b>mode</b> (0=RESONATOR, 1=KARPLUS_STRONG): Structural model toggle.
 * </ul>
 */
public class RingsReverb extends StereoUGen {

  // ─── Parameters ───────────────────────────────────────────────
  private float brightness = 0.5f;
  private float position = 0.5f;
  private float structure = 0.5f;
  private float damping = 0.5f;
  private float excitation = 0.0f;
  private int mode = 0; // 0=RESONATOR, 1=KARPLUS_STRONG

  // ─── Internal state ───────────────────────────────────────────
  private final float sampleRate;
  private float baseFreq = 440.0f;

  // 4 parallel modal resonators (stereo pairs) — used in RESONATOR mode
  private final ModalResonator[] resL = new ModalResonator[4];
  private final ModalResonator[] resR = new ModalResonator[4];

  // Karplus-Strong string model — used in KARPLUS_STRONG mode
  private final KarplusStrongString ksString;

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
  private int prevMode = -1;

  // ─── YIN autocorrelation pitch tracking state ─────────────────
  private static final int YIN_BUFFER_SIZE = 2048;
  private final float[] yinBuffer = new float[YIN_BUFFER_SIZE];
  private int yinWriteIdx = 0;
  private float yinPrevEstimate = 440f;
  private static final float YIN_THRESHOLD = 0.15f;

  // ─── Mallet excitation state ──────────────────────────────────
  private float malletEnvelope = 0f;
  private float prevEnvelope = 0f;
  private static final float MALLET_DECAY = 0.9995f; // per-sample decay
  private static final float TRANSIENT_THRESHOLD = 0.02f;

  public RingsReverb() {
    this(44100f);
  }

  public RingsReverb(float sampleRate) {
    this.sampleRate = sampleRate;
    ksString = new KarplusStrongString(sampleRate, 440f);

    for (int i = 0; i < 4; i++) {
      resL[i] = new ModalResonator(sampleRate);
      resR[i] = new ModalResonator(sampleRate);
    }

    // Tank reverb: 4 parallel combs + 4 series allpass per channel
    int[] combTaps = {480, 600, 700, 800};
    int[] apTaps = {160, 140, 120, 100};

    tankCombL = new CombFilter[4];
    tankCombR = new CombFilter[4];
    tankApL = new AllPass[4];
    tankApR = new AllPass[4];

    for (int i = 0; i < 4; i++) {
      tankCombL[i] = new CombFilter(combTaps[i], 0.6f);
      tankCombR[i] = new CombFilter(combTaps[i] + 11, 0.6f);
      tankApL[i] = new AllPass(apTaps[i], 0.3f);
      tankApR[i] = new AllPass(apTaps[i] + 7, 0.3f);
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

  /**
   * Mallet excitation amount (0=none, 1=full). Triggers an exponential-decay noise burst on
   * transients.
   */
  public void setExcitation(float v) {
    this.excitation = Math.max(0f, Math.min(1f, v));
  }

  /** Structural model: 0=RESONATOR (modal bandpass), 1=KARPLUS_STRONG (string model). */
  public void setMode(int v) {
    this.mode = v == 0 ? 0 : 1;
  }

  // ─── StereoUGen implementation ────────────────────────────────

  @Override
  protected void computeStereo(float input, long systemTime) {
    computeStereo(input, input, systemTime);
  }

  @Override
  protected void computeStereo(float left, float right, long systemTime) {
    // DC blocker
    left -= dcBlockerL * DC_BLOCK;
    right -= dcBlockerR * DC_BLOCK;
    dcBlockerL = left + dcBlockerL * DC_BLOCK;
    dcBlockerR = right + dcBlockerR * DC_BLOCK;

    float mono = (left + right) * 0.5f;

    // YIN autocorrelation pitch estimation (replaces zero-crossing)
    estimatePitchYin(mono);

    // Update structure on param change (includes mode switch)
    updateResonators();

    // Mallet excitation: detect transient onset, fire noise burst
    float malletSignal = computeMallet(mono);

    float resOutL = 0f, resOutR = 0f;

    if (mode == 1) {
      // ── Karplus-Strong mode ──
      float ksOut = ksString.tick(mono + malletSignal);
      // Route through tank tail for body
      float tankInL = ksOut;
      float tankInR = ksOut;
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
      // Crossfade: position controls direct string vs string+tank
      resOutL = ksOut * (1f - position) + tankOutL * position;
      resOutR = ksOut * (1f - position) + tankOutR * position;
    } else {
      // ── RESONATOR mode (original) ──
      for (int i = 0; i < 4; i++) {
        resOutL += resL[i].tick(mono);
        resOutR += resR[i].tick(mono);
      }
      float resScale = 0.25f;
      resOutL *= resScale;
      resOutR *= resScale;

      // Process through tank reverb
      float tankInL = resOutL;
      float tankInR = resOutR;
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

      // Crossfade: position controls balance between dry resonators and wet
      resOutL = resOutL * (1f - position) + tankOutL * position;
      resOutR = resOutR * (1f - position) + tankOutR * position;

      // Add mallet signal to resonator output at the end
      resOutL += malletSignal * 0.3f;
      resOutR += malletSignal * 0.3f;
    }

    // Apply brightness as a post-process gain
    float gain = 0.3f + brightness * 0.7f;
    resOutL *= gain;
    resOutR *= gain;

    lastOutChannels[0] = resOutL;
    lastOutChannels[1] = resOutR;
  }

  // ─── YIN autocorrelation pitch estimation ─────────────────────
  //
  // YIN (De Cheveigné & Kawahara, 2002) is an autocorrelation-based pitch estimator
  // that is more robust than zero-crossing — better octave detection, less jitter,
  // and works on polyphonic/mixed signals.
  //
  // Simplified implementation: difference function + cumulative mean normalization
  // over a 2048-sample buffer. The first minimum below threshold is the period.

  private void estimatePitchYin(float s) {
    yinBuffer[yinWriteIdx] = s;
    yinWriteIdx = (yinWriteIdx + 1) % YIN_BUFFER_SIZE;

    // Only run estimation every 512 samples to keep CPU light
    if (yinWriteIdx % 512 != 0) return;

    // Difference function: d(tau) = sum_{j=0}^{N/2-1} (x[j] - x[j+tau])^2
    // Use last YIN_BUFFER_SIZE/2 samples as frame
    int half = YIN_BUFFER_SIZE / 2;
    float[] diff = new float[half];
    float runningMin = Float.MAX_VALUE;
    int bestTau = 0;

    for (int tau = 1; tau < half; tau++) {
      float sum = 0f;
      for (int j = 0; j < half; j++) {
        int idxJ = (yinWriteIdx - half + j + YIN_BUFFER_SIZE) % YIN_BUFFER_SIZE;
        int idxT = (idxJ + tau) % YIN_BUFFER_SIZE;
        float d = yinBuffer[idxJ] - yinBuffer[idxT];
        sum += d * d;
      }
      diff[tau] = sum;
    }

    // Cumulative mean normalization (CMN): d'(tau) = d(tau) / ( (1/tau) * sum_{j=1}^{tau} d(j) )
    float cumSum = 0f;
    for (int tau = 1; tau < half; tau++) {
      cumSum += diff[tau];
      if (cumSum < 1e-10f) continue;
      float cmn = diff[tau] * tau / cumSum;
      if (cmn < runningMin) {
        runningMin = cmn;
        bestTau = tau;
      }
      // First minimum below threshold is the period
      if (cmn < YIN_THRESHOLD && tau > 20) { // tau > 20 avoids subharmonics at very low freqs
        bestTau = tau;
        break;
      }
    }

    if (bestTau > 0) {
      float freq = sampleRate / bestTau;
      // Parabolic interpolation around the minimum for sub-sample accuracy
      if (bestTau > 1 && bestTau < half - 1) {
        float y0 = diff[bestTau - 1];
        float y1 = diff[bestTau];
        float y2 = diff[bestTau + 1];
        float a = (y0 + y2 - 2f * y1) * 0.5f;
        if (a != 0f) {
          float delta = (y0 - y2) / (2f * a);
          float interpolatedTau = bestTau + delta;
          if (interpolatedTau > 0) {
            freq = sampleRate / interpolatedTau;
          }
        }
      }
      // Clamp and smooth
      freq = Math.max(30f, Math.min(8000f, freq));
      // Low-pass filter the estimate (75% new, 25% previous) to avoid jitter
      baseFreq = freq * 0.75f + yinPrevEstimate * 0.25f;
      yinPrevEstimate = baseFreq;
    }
  }

  // ─── Mallet excitation ────────────────────────────────────────
  //
  // Monitors the input envelope. On transient detection (rapid rise in energy),
  // triggers an exponentially-decaying white noise burst — simulating the "strike"
  // sound of a mallet hitting the resonator/string.

  private float computeMallet(float s) {
    // Simple envelope follower (rectify + one-pole)
    float instantEnv = Math.abs(s);
    float envelope = instantEnv + prevEnvelope * 0.5f; // naive envelope
    envelope = Math.max(instantEnv, envelope * 0.999f); // fast attack, slow decay-ish

    // Detect transient: envelope rise above threshold
    if (excitation > 0.01f && envelope > TRANSIENT_THRESHOLD && envelope > prevEnvelope * 2.5f) {
      // Trigger mallet: random noise burst at excitation amplitude
      malletEnvelope = excitation * 0.5f;
    }

    prevEnvelope = envelope;

    // Generate noise burst with exponential decay
    if (malletEnvelope > 0.001f) {
      float noise = (float) (Math.random() * 2f - 1f) * malletEnvelope;
      malletEnvelope *= MALLET_DECAY; // per-sample decay
      return noise;
    }
    malletEnvelope = 0f;
    return 0f;
  }

  // ─── Internal: update resonator tuning ────────────────────────

  private void updateResonators() {
    if (mode == 1) {
      // ── Karplus-Strong mode ──
      ksString.setFreq(baseFreq);
      // brightness controls string damping (higher = less damped = brighter)
      ksString.setDamping(1f - 0.95f * brightness);
    }

    if (brightness == prevBrightness && structure == prevStructure && mode == prevMode) return;
    prevBrightness = brightness;
    prevStructure = structure;
    prevMode = mode;

    // Harmonic ratios: structure=0 → inharmonic (metallic), structure=1 → harmonic (string)
    float[] inharmRatios = {1f, 4.02f, 8.03f, 12.04f};
    float[] harmRatios = {1f, 2f, 4f, 6f};

    float[] ratios = new float[4];
    for (int i = 0; i < 4; i++) {
      ratios[i] = inharmRatios[i] + (harmRatios[i] - inharmRatios[i]) * structure;
    }

    // Brightness controls resonator damping/Q
    // brightness=0 → heavily damped (dull), brightness=1 → lightly damped (bright)
    float q = 5f + brightness * 45f;
    float decay = 0.3f + (1f - brightness) * 0.6f;

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
   * ModalResonator: 2nd-order IIR bandpass with configurable frequency, Q, and decay envelope. Acts
   * as a struck resonator body — rings at its tuned frequency when excited by input.
   */
  private static class ModalResonator {
    private final float fs;
    private float b0, b1, b2, a1, a2;
    private float x1 = 0f, x2 = 0f, y1 = 0f, y2 = 0f;
    private float decay = 0.5f;

    ModalResonator(float sampleRate) {
      this.fs = sampleRate;
      setFreq(440f);
      setQ(20f);
    }

    void setFreq(float freq) {
      float w0 = (float) (2.0 * Math.PI * freq / fs);
      float alpha = (float) Math.sin(w0) * 0.5f;
      float cosW0 = (float) Math.cos(w0);
      b0 = alpha;
      b1 = 0f;
      b2 = -alpha;
      a1 = -2f * cosW0;
      a2 = 1f - 2f * alpha;
    }

    void setQ(float q) {
      float alpha = b0;
      float qScale = 1f - 1f / q;
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
      float output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
      output *= decay;
      x2 = x1;
      x1 = input;
      y2 = y1;
      y1 = output;
      return output;
    }
  }

  /**
   * KarplusStrongString: digital waveguide string model. Uses a delay line (period = sampleRate /
   * freq) with a lowpass filter in the feedback loop. The initial pluck is the input signal; the
   * string rings at its tuned frequency.
   */
  private static class KarplusStrongString {
    private final float[] delayLine;
    private int writeIdx = 0;
    private float feedback = 0.9f;
    private float lpState = 0f; // one-pole lowpass for damping
    private static final float LP_COEF = 0.5f;

    KarplusStrongString(float sampleRate, float freq) {
      int delayLen = Math.max(2, Math.round(sampleRate / freq));
      delayLine = new float[delayLen];
    }

    void setFreq(float freq) {
      // Current delay length is fixed at construction — for dynamic tuning
      // we'd need interpolation. For now, RG updates damping only.
    }

    void setDamping(float d) {
      // d=0 → maximum damping (quick decay), d=1 → minimum damping (long ring)
      this.feedback = 0.95f * d; // range 0..0.95
    }

    float tick(float input) {
      int len = delayLine.length;
      // Read from delay line
      float out = delayLine[writeIdx];
      // One-pole lowpass in feedback path (simulates string stiffness loss)
      lpState = lpState + LP_COEF * (out - lpState);
      // Write: input excites the string, feedback sustains it
      delayLine[writeIdx] = input + lpState * feedback;
      writeIdx = (writeIdx + 1) % len;
      return out;
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
