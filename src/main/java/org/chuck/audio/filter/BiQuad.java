package org.chuck.audio.filter;

import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;

/**
 * Biquad (second-order IIR) resonator filter. Supports prad (pole radius), pfreq (pole frequency
 * Hz), gain, and eqzs.
 */
public class BiQuad extends ChuckUGen {
  private double prad = 0.0;
  private double pfreq = 0.0;
  private boolean eqzs = false;

  // Biquad state
  private double x1 = 0, x2 = 0;
  private double y1 = 0, y2 = 0;

  // Coefficients (direct form I)
  private double b0 = 1, b1 = 0, b2 = 0; // feedforward
  private double a1 = 0, a2 = 0; // feedback (a0 = 1 normalised)

  private final float sampleRate;

  public BiQuad(float sampleRate) {
    this.sampleRate = sampleRate;
  }

  public void setPrad(double r) {
    this.prad = r;
    updateCoeffs();
  }

  public void setPfreq(double f) {
    this.pfreq = f;
    updateCoeffs();
  }

  public void setEqzs(double v) {
    this.eqzs = v != 0.0;
    updateCoeffs();
  }

  /** ChucK-style: z.radius(0.99) */
  public double radius(double r) {
    setPrad(r);
    return r;
  }

  /** ChucK-style: z.freq(440) */
  public double freq(double f) {
    setPfreq(f);
    return f;
  }

  /** ChucK-style: z.norm(1) */
  public double norm(double v) {
    setEqzs(v);
    return v;
  }

  public double getPrad() {
    return prad;
  }

  public double getPfreq() {
    return pfreq;
  }

  private void updateCoeffs() {
    if (pfreq <= 0 || sampleRate <= 0) {
      b0 = 1;
      b1 = 0;
      b2 = 0;
      a1 = 0;
      a2 = 0;
      return;
    }
    double omega = 2.0 * Math.PI * pfreq / sampleRate;
    a1 = -2.0 * prad * Math.cos(omega);
    a2 = prad * prad;
    if (eqzs) {
      b0 = 1.0;
      b1 = 0.0;
      b2 = -1.0;
    } else {
      b0 = 1;
      b1 = 0;
      b2 = 0;
    }
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

    // 2. Apply filter (recursive, scalar)
    for (int i = 0; i < length; i++) {
      double x0 = inputSum[i];
      double y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
      x2 = x1;
      x1 = x0;
      y2 = y1;
      y1 = y0;
      blockCache[i] = (float) y0;
      if (buffer != null) buffer[offset + i] = blockCache[i];
    }

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = (systemTime == -1) ? -1 : systemTime + length - 1;
    if (length > 0) lastOut = blockCache[length - 1];
  }

  @Override
  protected float compute(float input, long systemTime) {
    double x0 = input;
    double y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
    x2 = x1;
    x1 = x0;
    y2 = y1;
    y1 = y0;
    return (float) y0;
  }
}
