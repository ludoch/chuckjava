package org.chuck.audio.util;

import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;

/** A specialized UGen for DAC channels that supports vectorized summing. */
public class DacChannel extends ChuckUGen {
  private final int channelIndex;

  public DacChannel(int channelIndex) {
    this.channelIndex = channelIndex;
  }

  @Override
  public float getChannelLastOut(int i) {
    if (i == channelIndex) return lastOut;
    return 0.0f;
  }

  @Override
  protected float compute(float input, long systemTime) {
    return input;
  }

  @Override
  public float tick(long systemTime) {
    float sum = 0.0f;
    for (ChuckUGen src : sources) {
      src.tick(systemTime);
      sum += src.getChannelLastOut(channelIndex);
    }

    lastOut = sum * gain;
    lastTickTime = systemTime;
    return lastOut;
  }

  @Override
  public void tick(float[] buffer, int offset, int length, long systemTime) {
    // Initialize buffer with zeros
    for (int i = 0; i < length; i++) buffer[offset + i] = 0.0f;

    // Vectorized summing from all sources
    for (ChuckUGen src : sources) {
      float[] temp = new float[length];

      // Only the first channel triggers the tick on sources
      if (channelIndex == 0) {
        src.tick(temp, 0, length, systemTime);
      } else {
        // Other channels must use the cached data if source is multi-channel,
        // or just re-read if it was already ticked.
        // For simplicity in this demo, we'll let each channel tick if it wasn't ticked yet
        src.tick(temp, 0, length, systemTime);
      }

      // SIMD Addition: buffer += temp
      int i = 0;
      int bound = SPECIES.loopBound(length);
      for (; i < bound; i += SPECIES.length()) {
        FloatVector vSum = FloatVector.fromArray(SPECIES, buffer, offset + i);
        FloatVector vSrc = FloatVector.fromArray(SPECIES, temp, i);
        vSum.add(vSrc).intoArray(buffer, offset + i);
      }
      // Fallback
      for (; i < length; i++) {
        buffer[offset + i] += temp[i];
      }
    }

    // Apply gain (vectorized)
    if (gain != 1.0f) {
      int i = 0;
      int bound = SPECIES.loopBound(length);
      FloatVector vGain = FloatVector.broadcast(SPECIES, gain);
      for (; i < bound; i += SPECIES.length()) {
        FloatVector v = FloatVector.fromArray(SPECIES, buffer, offset + i);
        v.mul(vGain).intoArray(buffer, offset + i);
      }
      for (; i < length; i++) buffer[offset + i] *= gain;
    }

    // Cache lastOut for scalar callers
    if (length > 0) {
      lastOut = buffer[offset + length - 1];
      lastTickTime = systemTime + length - 1;
    }
  }
}
