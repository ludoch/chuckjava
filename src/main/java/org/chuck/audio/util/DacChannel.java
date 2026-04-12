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

  public void tick(
      java.lang.foreign.MemorySegment outSeg,
      int bufferIndex,
      int length,
      long systemTime,
      float masterGain) {
    float[] block = new float[length];
    // Summon audio from sources into block
    for (ChuckUGen src : sources) {
      float[] temp = new float[length];
      src.tick(temp, 0, length, systemTime);
      // SIMD Addition: block += temp
      int i = 0;
      int bound = SPECIES.loopBound(length);
      for (; i < bound; i += SPECIES.length()) {
        FloatVector vBlock = FloatVector.fromArray(SPECIES, block, i);
        FloatVector vSrc = FloatVector.fromArray(SPECIES, temp, i);
        vBlock.add(vSrc).intoArray(block, i);
      }
      for (; i < length; i++) block[i] += temp[i];
    }

    // Apply local gain and master gain, then write to outSeg
    float totalGain = gain * masterGain;
    for (int i = 0; i < length; i++) {
      float sample = block[i] * totalGain;
      short s16 = (short) (Math.max(-1f, Math.min(1f, sample)) * 32767f);

      // Fill visualization buffer
      visBuffer[visWriteIdx] = sample;
      visWriteIdx = (visWriteIdx + 1) % visBuffer.length;

      // Write interleaved
      // Total offset: (bufferIndex + i) * numChannels * 2 + (channelIndex * 2)
      // Assuming 2 channels for now as per ChuckAudio hardcoding
      long offset = (long) ((bufferIndex + i) * 2 + channelIndex) * 2;
      outSeg.set(
          java.lang.foreign.ValueLayout.JAVA_SHORT.withOrder(java.nio.ByteOrder.LITTLE_ENDIAN),
          offset,
          s16);

      if (i == length - 1) {
        lastOut = sample;
        lastTickTime = systemTime + length - 1;
      }
    }
  }

  private final float[] visBuffer = new float[2048];
  private int visWriteIdx = 0;

  public float[] getVisBuffer() {
    float[] copy = new float[visBuffer.length];
    System.arraycopy(visBuffer, 0, copy, 0, visBuffer.length);
    return copy;
  }

  @Override
  public void tick(float[] buffer, int offset, int length, long systemTime) {

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
    // Apply gain and write to output buffer
    for (int i = 0; i < length; i++) {
      float sample = buffer[offset + i] * gain;
      buffer[offset + i] = sample;

      // Fill visualization buffer
      visBuffer[visWriteIdx] = sample;
      visWriteIdx = (visWriteIdx + 1) % visBuffer.length;
    }

    // Cache lastOut for scalar callers
    if (length > 0) {
      lastOut = buffer[offset + length - 1];
      lastTickTime = systemTime + length - 1;
    }
  }
}
