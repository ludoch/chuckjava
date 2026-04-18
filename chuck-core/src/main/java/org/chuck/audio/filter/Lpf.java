package org.chuck.audio.filter;

import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/** A simple Low Pass Filter (One-pole). */
@doc("Low Pass Filter (1-pole).")
public class Lpf extends ChuckUGen {
  private float cutoff = 1000.0f;
  private float sampleRate;

  // Filter state
  private float v0 = 0.0f;

  public Lpf(float sampleRate) {
    this.sampleRate = sampleRate;
  }

  public void setCutoff(float cutoff) {
    this.cutoff = cutoff;
  }

  public double freq(double f) {
    setCutoff((float) f);
    return f;
  }

  public double freq() {
    return cutoff;
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

    // 2. Apply filter
    float alpha = (float) (2.0 * Math.PI * cutoff / sampleRate);
    alpha = Math.min(Math.max(alpha, 0.0f), 1.0f);
    float beta = 1.0f - alpha;

    float localV0 = v0;
    for (int i = 0; i < length; i++) {
      localV0 = alpha * inputSum[i] + beta * localV0;
      blockCache[i] = localV0 * gain;
      if (buffer != null) buffer[offset + i] = blockCache[i];
    }
    v0 = localV0;

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = (systemTime == -1) ? -1 : systemTime + length - 1;
    if (length > 0) lastOut = blockCache[length - 1];
  }

  @Override
  protected float compute(float input, long systemTime) {
    float alpha = (float) (2.0 * Math.PI * cutoff / sampleRate);
    alpha = Math.min(Math.max(alpha, 0.0f), 1.0f);
    v0 = v0 + alpha * (input - v0);
    return v0;
  }
}
