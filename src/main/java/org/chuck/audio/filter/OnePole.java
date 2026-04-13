package org.chuck.audio.filter;

import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;

/** A one-pole digital filter. y[n] = b0 * x[n] - a1 * y[n-1] */
public class OnePole extends ChuckUGen {
  private float b0 = 1.0f;
  private float a1 = 0.0f;
  private float lastOutput = 0.0f;

  public void setB0(float b0) {
    this.b0 = b0;
  }

  public void setA1(float a1) {
    this.a1 = a1;
  }

  private float sampleRate = 44100.0f;

  public OnePole() {}

  public OnePole(float sampleRate) {
    this.sampleRate = sampleRate;
  }

  public double freq(double f) {
    float pole = (float) Math.exp(-2.0 * Math.PI * f / sampleRate);
    setPole(pole);
    return f;
  }

  public void setPole(float pole) {
    if (pole > 0.0f) b0 = 1.0f - pole;
    else b0 = 1.0f + pole;
    a1 = -pole;
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

    // 2. Apply filter
    float localB0 = b0;
    float localA1 = a1;
    float localLastOutput = lastOutput;
    float localGain = gain;

    for (int i = 0; i < length; i++) {
      localLastOutput = localB0 * inputSum[i] - localA1 * localLastOutput;
      blockCache[i] = localLastOutput * localGain;
      if (buffer != null) buffer[offset + i] = blockCache[i];
    }
    lastOutput = localLastOutput;

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = (systemTime == -1) ? -1 : systemTime + length - 1;
    if (length > 0) lastOut = blockCache[length - 1];
  }

  @Override
  protected float compute(float input, long systemTime) {
    lastOutput = b0 * input - a1 * lastOutput;
    return lastOutput;
  }
}
