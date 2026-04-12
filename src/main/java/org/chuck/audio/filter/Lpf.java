package org.chuck.audio.filter;

import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;

/** A simple Low Pass Filter. */
public class Lpf extends ChuckUGen {
  private float cutoff = 1000.0f;

  @SuppressWarnings("unused")
  private float resonance = 1.0f;

  private float sampleRate;

  // Filter state
  private float v0 = 0.0f;

  @SuppressWarnings("unused")
  private float v1 = 0.0f;

  public Lpf(float sampleRate) {
    this.sampleRate = sampleRate;
  }

  public void setCutoff(float cutoff) {
    this.cutoff = cutoff;
  }

  @Override
  public void tick(float[] buffer, int offset, int length, long systemTime) {
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
      // If no sources, copy from input buffer (which might have direct chuckTo data)
      System.arraycopy(buffer, offset, inputSum, 0, length);
    }

    // 2. Apply filter
    float alpha = (float) (2.0 * Math.PI * cutoff / sampleRate);
    alpha = Math.min(Math.max(alpha, 0.0f), 1.0f);
    float beta = 1.0f - alpha;

    float localV0 = v0;
    for (int i = 0; i < length; i++) {
      localV0 = alpha * inputSum[i] + beta * localV0;
      buffer[offset + i] = localV0;
    }
    v0 = localV0;

    lastOut = v0;
    lastTickTime = (systemTime == -1) ? -1 : systemTime + length - 1;
  }

  @Override
  protected float compute(float input, long systemTime) {
    // Simple 1-pole low pass for demonstration
    float alpha = (float) (2.0 * Math.PI * cutoff / sampleRate);
    alpha = Math.min(Math.max(alpha, 0.0f), 1.0f);

    v0 = v0 + alpha * (input - v0);
    return v0;
  }
}
