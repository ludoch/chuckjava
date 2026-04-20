package org.chuck.audio.filter;

import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;

/**
 * An all-pass filter UGen. Adapted from STK. Uses double precision internally to prevent limit
 * cycles.
 */
public class AllPass extends ChuckUGen {
  private final double[] buffer;
  private int writePos = 0;
  private int delaySamples;
  private double coefficient = 0.7;

  public AllPass(int maxDelaySamples) {
    this(maxDelaySamples, true);
  }

  public AllPass(int maxDelaySamples, boolean autoRegister) {
    super(autoRegister);
    this.buffer = new double[maxDelaySamples];
    this.delaySamples = maxDelaySamples;
  }

  public void delay(double samples) {
    int s = (int) samples;
    if (s >= buffer.length) s = buffer.length - 1;
    if (s < 0) s = 0;
    this.delaySamples = s;
  }

  public void setCoefficient(double c) {
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

    // 2. Filter logic (double scalar recursive)
    for (int i = 0; i < length; i++) {
      double input = inputSum[i];

      int readPos = (writePos - delaySamples + this.buffer.length) % this.buffer.length;
      double temp = this.buffer[readPos];

      double inner = input + coefficient * temp;
      this.buffer[writePos] = inner;
      writePos = (writePos + 1) % this.buffer.length;

      double out = (-coefficient * inner + temp) * gain;

      blockCache[i] = (float) out;
      if (buffer != null) buffer[offset + i] = blockCache[i];
    }

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = (systemTime == -1) ? -1 : systemTime + length - 1;
    if (length > 0) lastOut = blockCache[length - 1];
  }

  @Override
  protected float compute(float input, long systemTime) {
    int readPos = (writePos - delaySamples + buffer.length) % buffer.length;
    double temp = buffer[readPos];

    double inner = input + coefficient * temp;
    buffer[writePos] = inner;
    writePos = (writePos + 1) % buffer.length;

    double out = -coefficient * inner + temp;
    return (float) out;
  }
}
