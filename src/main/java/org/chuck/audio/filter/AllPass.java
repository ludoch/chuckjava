package org.chuck.audio.filter;

import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;
import org.chuck.audio.fx.Delay;

/** An all-pass filter UGen. Adapted from STK. */
public class AllPass extends ChuckUGen {
  private final Delay delayLine;
  private float coefficient = 0.7f;

  public AllPass(int delaySamples) {
    this.delayLine = new Delay(delaySamples);
  }

  public void setCoefficient(float c) {
    this.coefficient = c;
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

    if (blockCache == null || blockCache.length < length) {
      blockCache = new float[length];
    }

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

    // 2. Filter logic (scalar recursive)
    for (int i = 0; i < length; i++) {
      float input = inputSum[i];
      float temp = delayLine.getLastOut();
      float inner = input + coefficient * temp;
      delayLine.tick(inner, systemTime == -1 ? -1 : systemTime + i);
      float out = (-coefficient * inner + temp) * gain;
      blockCache[i] = out;
      if (buffer != null) buffer[offset + i] = out;
    }

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = (systemTime == -1) ? -1 : systemTime + length - 1;
    if (length > 0) lastOut = blockCache[length - 1];
  }

  @Override
  protected float compute(float input, long systemTime) {
    float temp = delayLine.getLastOut();
    float inner = input + coefficient * temp;
    delayLine.tick(inner, systemTime);
    return -coefficient * inner + temp;
  }
}
