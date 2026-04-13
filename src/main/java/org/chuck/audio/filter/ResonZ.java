package org.chuck.audio.filter;

import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;

/** A resonance filter. Adapted from SuperCollider's ResonZ via ChucK. */
public class ResonZ extends ChuckUGen {
  private float freq = 220.0f;
  private float Q = 1.0f;
  private float a0, b1, b2;
  private float y1, y2;
  private final float sampleRate;

  public ResonZ(float sampleRate) {
    this.sampleRate = sampleRate;
    set(freq, Q);
  }

  public void set(float freq, float Q) {
    this.freq = freq;
    this.Q = Q;

    double radiansPerSample = 2.0 * Math.PI / sampleRate;
    double pfreq = freq * radiansPerSample;
    double B = pfreq / Q;
    double R = 1.0 - B * 0.5;
    double R2 = 2.0 * R;
    double R22 = R * R;
    double cost = (R2 * Math.cos(pfreq)) / (1.0 + R22);

    this.b1 = (float) (R2 * cost);
    this.b2 = (float) (-R22);
    this.a0 = (float) ((1.0 - R22) * 0.5);
  }

  public void setFreq(float freq) {
    set(freq, this.Q);
  }

  public void setQ(float Q) {
    set(this.freq, Q);
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
      float y0 = inputSum[i] + b1 * y1 + b2 * y2;
      float result = a0 * (y0 - y2);
      y2 = y1;
      y1 = y0;
      blockCache[i] = result;
      if (buffer != null) buffer[offset + i] = blockCache[i];
    }

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = (systemTime == -1) ? -1 : systemTime + length - 1;
    if (length > 0) lastOut = blockCache[length - 1];
  }

  @Override
  protected float compute(float input, long systemTime) {
    float y0 = input + b1 * y1 + b2 * y2;
    float result = a0 * (y0 - y2);
    y2 = y1;
    y1 = y0;
    return result;
  }
}
