package org.chuck.audio.filter;

import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;

/** A one-zero digital filter. y[n] = b0 * x[n] + b1 * x[n-1] */
public class OneZero extends ChuckUGen {
  private float b0 = 1.0f;
  private float b1 = 0.0f;
  private float lastInput = 0.0f;

  public void setB0(float b0) {
    this.b0 = b0;
  }

  public void setB1(float b1) {
    this.b1 = b1;
  }

  public void setZero(float zero) {
    if (zero > 0.0f) b0 = 1.0f / (1.0f + zero);
    else b0 = 1.0f / (1.0f - zero);
    b1 = -zero * b0;
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
    float localB0 = b0;
    float localB1 = b1;
    float localLastInput = lastInput;
    float localGain = gain;

    for (int i = 0; i < length; i++) {
      float currentInput = inputSum[i];
      float out = localB0 * currentInput + localB1 * localLastInput;
      localLastInput = currentInput;
      blockCache[i] = out * localGain;
      if (buffer != null) buffer[offset + i] = blockCache[i];
    }
    lastInput = localLastInput;

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = (systemTime == -1) ? -1 : systemTime + length - 1;
    if (length > 0) lastOut = blockCache[length - 1];
  }

  @Override
  protected float compute(float input, long systemTime) {
    float out = b0 * input + b1 * lastInput;
    lastInput = input;
    return out;
  }
}
