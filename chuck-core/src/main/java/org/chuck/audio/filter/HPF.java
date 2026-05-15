package org.chuck.audio.filter;

import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;

/**
 * High-pass filter with ZDF (Zero-Delay Feedback) SVF topology.
 *
 * Controls: freq (cutoff Hz), Q (resonance), morph (0.0=LP → 0.5=BP → 1.0=HP), notchMode,
 * drive (1.0=linear, >1.0=tanh saturation).
 *
 * Default morph=1.0 preserves HPF behavior for existing callers. The ZDF SVF naturally computes
 * LP, BP, and HP outputs simultaneously, enabling continuous morphing between filter types.
 */
public class HPF extends ChuckUGen {
  private double cutoff;
  private double q;
  private double morph = 1.0; // 0=LP, 0.5=BP, 1.0=HP (default HPF)
  private boolean notchMode = false;
  private float drive = 1.0f;
  private final float sampleRate;

  // ZDF integrator state
  private double ic1eq = 0.0;
  private double ic2eq = 0.0;

  public HPF(float sampleRate) {
    this.sampleRate = sampleRate;
    this.cutoff = 1000.0;
    this.q = 0.707;
  }

  public double freq(double f) {
    setFreq(f);
    return f;
  }

  public double freq() {
    return cutoff;
  }

  public void setFreq(double f) {
    cutoff = f;
  }

  public double getFreq() {
    return cutoff;
  }

  public double Q(double qv) {
    setQ(qv);
    return qv;
  }

  public double Q() {
    return q;
  }

  public void setQ(double qv) {
    q = qv;
  }

  public double getQ() {
    return q;
  }

  public double morph(double m) {
    this.morph = Math.max(0.0, Math.min(1.0, m));
    return this.morph;
  }

  public double morph() {
    return morph;
  }

  public void notchMode(boolean b) {
    this.notchMode = b;
  }

  public boolean notchMode() {
    return notchMode;
  }

  public void drive(float d) {
    this.drive = Math.max(0.0f, Math.min(2.0f, d));
  }

  public float drive() {
    return drive;
  }

  public void reset() {
    ic1eq = 0.0;
    ic2eq = 0.0;
  }

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

    // 1. Sum inputs using SIMD
    float[] inputSum = new float[length];
    if (getNumSources() > 0) {
      for (ChuckUGen src : getSources()) {
        float[] temp = new float[length];
        src.tick(temp, 0, length, systemTime);

        int i = 0;
        int bound = SPECIES.loopBound(length);
        for (; i < bound; i += SPECIES.length()) {
          FloatVector v1 = FloatVector.fromArray(SPECIES, inputSum, i);
          FloatVector v2 = FloatVector.fromArray(SPECIES, temp, i);
          v1.add(v2).intoArray(inputSum, i);
        }
        for (; i < length; i++) inputSum[i] += temp[i];
      }
    } else {
      if (buffer != null) System.arraycopy(buffer, offset, inputSum, 0, length);
    }

    // 2. ZDF SVF processing
    double m = this.morph;
    double cLow = m <= 0.5 ? 1.0 - 2.0 * m : 0.0;
    double cBand = m <= 0.5 ? 2.0 * m : 1.0 - 2.0 * (m - 0.5);
    double cHigh = m <= 0.5 ? 0.0 : 2.0 * (m - 0.5);

    double safeCutoff = Math.max(10.0, Math.min(sampleRate * 0.49, cutoff));
    double g = Math.tan(Math.PI * safeCutoff / (sampleRate * 2.0));
    double R = 1.0 / (2.0 * Math.max(0.1, q));
    double denom = 1.0 / (1.0 + 2.0 * R * g + g * g);

    for (int i = 0; i < length; i++) {
      double in = inputSum[i] * drive;
      double out = 0.0;

      // Double sampling loop (linear when drive <= 1.0)
      boolean saturate = drive > 1.0f;
      for (int step = 0; step < 2; step++) {
        double hp = (in - 2.0 * R * ic1eq - g * ic1eq - ic2eq) * denom;
        double bp = ic1eq + g * hp;
        if (saturate) bp = Math.tanh(bp);
        double lp = ic2eq + g * bp;

        ic1eq = 2.0 * bp - ic1eq;
        ic2eq = 2.0 * lp - ic2eq;
        if (Math.abs(ic1eq) < 1.0e-15) ic1eq = 0.0;
        if (Math.abs(ic2eq) < 1.0e-15) ic2eq = 0.0;

        if (step == 1) {
          if (notchMode) {
            out = lp + hp;
          } else {
            out = cLow * lp + cBand * bp + cHigh * hp;
          }
        }
      }

      if (Math.abs(out) < 1.0e-15) out = 0.0;
      if (saturate) out = Math.tanh(out * 2.0) / 2.0;
      if (out > 2.0) out = 2.0;
      if (out < -2.0) out = -2.0;

      blockCache[i] = (float) out * gain;
      if (buffer != null) buffer[offset + i] = blockCache[i];
    }

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = (systemTime == -1) ? -1 : systemTime + length - 1;
    if (length > 0) lastOut = blockCache[length - 1];
  }

  @Override
  protected float compute(float input, long systemTime) {
    double m = this.morph;
    double cLow = m <= 0.5 ? 1.0 - 2.0 * m : 0.0;
    double cBand = m <= 0.5 ? 2.0 * m : 1.0 - 2.0 * (m - 0.5);
    double cHigh = m <= 0.5 ? 0.0 : 2.0 * (m - 0.5);

    double safeCutoff = Math.max(10.0, Math.min(sampleRate * 0.49, cutoff));
    double g = Math.tan(Math.PI * safeCutoff / (sampleRate * 2.0));
    double R = 1.0 / (2.0 * Math.max(0.1, q));
    double denom = 1.0 / (1.0 + 2.0 * R * g + g * g);

    double in = input * drive;
    double out = 0.0;
    boolean saturate = drive > 1.0f;
    for (int step = 0; step < 2; step++) {
      double hp = (in - 2.0 * R * ic1eq - g * ic1eq - ic2eq) * denom;
      double bp = ic1eq + g * hp;
      if (saturate) bp = Math.tanh(bp);
      double lp = ic2eq + g * bp;

      ic1eq = 2.0 * bp - ic1eq;
      ic2eq = 2.0 * lp - ic2eq;

      if (step == 1) {
        if (notchMode) {
          out = lp + hp;
        } else {
          out = cLow * lp + cBand * bp + cHigh * hp;
        }
      }
    }
    if (saturate) out = Math.tanh(out * 2.0) / 2.0;
    return (float) out;
  }
}
