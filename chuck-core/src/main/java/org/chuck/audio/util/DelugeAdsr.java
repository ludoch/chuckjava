package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/**
 * ADSR Envelope matching the Synthstrom Deluge firmware.
 *
 * <p>Ported from the C++ Deluge Firmware's Envelope::render() (fixed-point Q31 arithmetic
 * converted to floating point). Uses a phase accumulator (pos, target 2^23 = 8388608)
 * and lookup-table-derived curves:
 * <ul>
 *   <li>Attack: decay4 curve (concave, fast at start)</li>
 *   <li>Decay: decay8 curve from 1.0 down to sustain level</li>
 *   <li>Sustain: holds at sustain level</li>
 *   <li>Release: exponential decay from current value to 0</li>
 *   <li>Fast Release: sine-based release for voice stealing (~5ms)</li>
 * </ul>
 *
 * <p>The output range is [0, 1] (unlike the firmware's centered output).
 */
@doc("Deluge firmware-correct ADSR with phase-accumulator curves.")
public class DelugeAdsr extends ChuckUGen {

  public static final int IDLE = 0;
  public static final int ATTACK = 1;
  public static final int DECAY = 2;
  public static final int SUSTAIN = 3;
  public static final int RELEASE = 4;
  public static final int FAST_RELEASE = 5;

  // Phase target: 2^23, matching firmware's 8388608
  private static final float PHASE_MAX = 8388608.0f;

  private float sampleRate;

  private volatile int state = IDLE;
  private double value = 0.0;      // Output value [0, 1]
  private double pos = 0.0;        // Phase accumulator (0 to PHASE_MAX)

  // Rate increments per sample (firmware: rate * numSamples added to pos each render call)
  // A rate of 1.0 means pos reaches PHASE_MAX in PHASE_MAX samples.
  // Converted from time-in-seconds: rate = PHASE_MAX / (timeSeconds * sampleRate)
  private double attackRate = 0.0;
  private double decayRate = 0.0;
  private double releaseRate = 0.0;
  private double fastReleaseRate = 0.0;

  private double sustainLevel = 0.7;

  // For release: lastValuePreCurrentStage = value at time of release trigger
  private double lastValuePreCurrentStage = 0.0;

  // Smoothed sustain (firmware uses this for click-free transitions)
  private double smoothedSustain = 0.0;

  public DelugeAdsr() {
    this(org.chuck.core.ChuckVM.CURRENT_VM.get().getSampleRate());
  }

  public DelugeAdsr(float sampleRate) {
    this.sampleRate = sampleRate;
    fastReleaseTime(0.005);
    set(0.01, 0.1, 0.7, 0.2);
  }

  /**
   * Convert time-in-seconds to a rate that reaches PHASE_MAX in that time.
   */
  private double timeToRate(double timeSeconds) {
    if (timeSeconds <= 0.0) return PHASE_MAX; // instant
    return PHASE_MAX / (timeSeconds * sampleRate);
  }

  public double attackTime(double timeSeconds) {
    this.attackRate = timeToRate(timeSeconds);
    return timeSeconds;
  }

  public double decayTime(double timeSeconds) {
    this.decayRate = timeToRate(timeSeconds);
    return timeSeconds;
  }

  public double sustainLevel(double level) {
    this.sustainLevel = Math.max(0.0, Math.min(1.0, level));
    return this.sustainLevel;
  }

  public double releaseTime(double timeSeconds) {
    this.releaseRate = timeToRate(timeSeconds);
    return timeSeconds;
  }

  public double fastReleaseTime(double timeSeconds) {
    this.fastReleaseRate = timeToRate(timeSeconds);
    return timeSeconds;
  }

  public void set(double a, double d, double s, double r) {
    attackTime(a);
    decayTime(d);
    sustainLevel(s);
    releaseTime(r);
  }

  // --- curve approximation functions (matching firmware LUT shapes) ---

  /**
   * decay4 curve: used for ATTACK.
   * Returns 1.0 at pos=0 down to ~0.0 at pos=PHASE_MAX.
   * Maps to a concave curve: starts fast, slows down.
   */
  private static double decay4(double pos) {
    double x = Math.max(0.0, Math.min(1.0, pos / PHASE_MAX));
    return Math.sqrt(1.0 - x * 0.85);
  }

  /**
   * decay8 curve: used for DECAY.
   * Returns 1.0 at pos=0 down to 0.0 at pos=PHASE_MAX.
   * Maps to a steeper curve than decay4.
   */
  private static double decay8(double pos) {
    double x = Math.max(0.0, Math.min(1.0, pos / PHASE_MAX));
    return Math.pow(1.0 - x, 1.25);
  }

  /**
   * Sine half-wave: used for FAST_RELEASE.
   * Returns 1.0 at pos=0 down to 0.0 at pos=PHASE_MAX.
   */
  private static double sineRelease(double pos) {
    double x = Math.max(0.0, Math.min(1.0, pos / PHASE_MAX));
    return 0.5 + 0.5 * Math.cos(x * Math.PI);
  }

  // --- state transitions ---

  public int keyOn() {
    pos = 0;
    state = ATTACK;
    value = 0;
    smoothedSustain = 0;
    return 1;
  }

  public int keyOn(int ignored) {
    return keyOn();
  }

  public void keyOff() {
    if (state != IDLE && state != RELEASE && state != FAST_RELEASE) {
      lastValuePreCurrentStage = value;
      pos = 0;
      state = RELEASE;
    }
  }

  public int keyOff(int ignored) {
    keyOff();
    return 1;
  }

  public void forceMute() {
    state = IDLE;
    value = 0.0;
    pos = 0.0;
  }

  public int forceMute(int ignored) {
    forceMute();
    return 1;
  }

  public int fastRelease() {
    if (state != IDLE) {
      lastValuePreCurrentStage = value;
      pos = 0;
      state = FAST_RELEASE;
    }
    return 1;
  }

  public int state() {
    return state;
  }

  public double value() {
    return value;
  }

  // --- per-sample compute (matching firmware's per-sample render loop) ---

  @Override
  public void tick(float[] buffer, int offset, int length, long systemTime) {
    if (systemTime != -1
        && systemTime == blockStartTime
        && blockCache != null
        && blockLength >= length) {
      if (buffer != null) System.arraycopy(blockCache, 0, buffer, offset, length);
      return;
    }

    if (blockCache == null || blockCache.length < length) blockCache = new float[length];

    // Sum sources into a temporary buffer
    java.util.List<ChuckUGen> srcs = getSources();
    if (srcs.isEmpty()) {
      for (int i = 0; i < length; i++) {
        float out = compute(0, systemTime + i) * gain;
        blockCache[i] = out;
        if (buffer != null) buffer[offset + i] = out;
      }
    } else {
      ChuckUGen src = srcs.get(0);
      float[] temp = new float[length];
      src.tick(temp, 0, length, systemTime);
      for (int i = 0; i < length; i++) {
        float out = compute(temp[i], systemTime + i) * gain;
        blockCache[i] = out;
        if (buffer != null) buffer[offset + i] = out;
      }
    }

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = systemTime + length - 1;
    if (length > 0) lastOut = blockCache[length - 1];
  }

  /**
   * Per-sample render, matching firmware's Envelope::render(numSamples=1).
   */
  @Override
  protected float compute(float input, long systemTime) {
    final double sustain = sustainLevel;

    switch (state) {
      case IDLE:
        value = 0.0;
        break;

      case ATTACK:
        pos += attackRate;
        if (pos >= PHASE_MAX) {
          pos = 0;
          value = 1.0;
          state = DECAY;
        } else {
          value = 1.0 - decay4(pos);
          if (value < 0.001) value = 0.001;
        }
        break;

      case DECAY:
        smoothedSustain += (sustain - smoothedSustain) * (1.0 / 512.0);
        value = smoothedSustain + decay8(pos) * (1.0 - smoothedSustain);
        pos += decayRate;
        if (pos >= PHASE_MAX) {
          state = SUSTAIN;
          smoothedSustain = sustain; // snap smoothing to avoid drop on entry
          value = sustain;
        }
        break;

      case SUSTAIN:
        smoothedSustain += (sustain - smoothedSustain) * (1.0 / 512.0);
        value = smoothedSustain;
        if (sustain == 0.0) {
          state = IDLE;
          value = 0.0;
        }
        break;

      case RELEASE:
        pos += releaseRate;
        if (pos >= PHASE_MAX) {
          state = IDLE;
          value = 0.0;
        } else {
          double releaseCurve = decay8(pos);
          value = releaseCurve * lastValuePreCurrentStage;
        }
        break;

      case FAST_RELEASE:
        pos += fastReleaseRate;
        if (pos >= PHASE_MAX) {
          state = IDLE;
          value = 0.0;
        } else {
          double f = sineRelease(pos);
          value = f * lastValuePreCurrentStage;
        }
        break;
    }

    // Apply envelope to input
    float out = (float) (input * value);
    if (Math.abs(out) < 1.0e-15f) out = 0.0f;
    return out;
  }
}
