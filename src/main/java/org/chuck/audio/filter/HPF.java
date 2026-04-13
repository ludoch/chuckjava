package org.chuck.audio.filter;

import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;

/**
 * Second-order Butterworth high-pass filter. Controls: freq (cutoff Hz), Q (resonance, default
 * 0.707).
 */
public class HPF extends ChuckUGen {
  private double cutoff;
  private double q;
  private final float sampleRate;

  private double b0, b1, b2;
  private double a1, a2;
  private double x1 = 0, x2 = 0, y1 = 0, y2 = 0;

  public HPF(float sampleRate) {
    this.sampleRate = sampleRate;
    this.cutoff = 1000.0;
    this.q = 0.707;
    updateCoeffs();
  }

  public double freq(double f) {
    cutoff = f;
    updateCoeffs();
    return f;
  }

  public double freq() {
    return cutoff;
  }

  public double Q(double qv) {
    q = qv;
    updateCoeffs();
    return qv;
  }

  public double Q() {
    return q;
  }

  private void updateCoeffs() {
    double w0 = 2.0 * Math.PI * cutoff / sampleRate;
    double cosW0 = Math.cos(w0);
    double alpha = Math.sin(w0) / (2.0 * q);
    double norm = 1.0 / (1.0 + alpha);
    b0 = (1.0 + cosW0) / 2.0 * norm;
    b1 = -(1.0 + cosW0) * norm;
    b2 = (1.0 + cosW0) / 2.0 * norm;
    a1 = -2.0 * cosW0 * norm;
    a2 = (1.0 - alpha) * norm;
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
      for (ChuckUGen src : sources) {
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
