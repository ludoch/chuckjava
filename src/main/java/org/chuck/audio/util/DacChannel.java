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
    if (systemTime != -1 && systemTime == lastTickTime) {
      return lastOut;
    }

    // Check if this sample is already in our block cache
    if (systemTime != -1
        && blockLength > 0
        && systemTime >= blockStartTime
        && systemTime < blockStartTime + blockLength) {
      int idx = (int) (systemTime - blockStartTime);
      lastOut = blockCache[idx];
      lastTickTime = systemTime;
      return lastOut;
    }

    if (isTicking) return lastOut;
    isTicking = true;

    try {
      float sum = 0.0f;
      java.util.List<ChuckUGen> srcs = getSources();
      for (ChuckUGen src : srcs) {
        src.tick(systemTime);
        sum += src.getChannelLastOut(channelIndex);
      }

      lastOut = sum * gain;
      lastTickTime = systemTime;
      blockStartTime = systemTime;
      blockLength = 0; // Scalar tick resets block window

      // Fill visualization buffer
      visBuffer[visWriteIdx] = lastOut;
      visWriteIdx = (visWriteIdx + 1) % visBuffer.length;

      return lastOut;
    } finally {
      isTicking = false;
    }
  }

  public void tick(
      java.lang.foreign.MemorySegment outSeg,
      int bufferIndex,
      int length,
      long systemTime,
      float masterGain) {
    float[] block = new float[length];
    // Summon audio from sources into block
    java.util.List<ChuckUGen> srcs = getSources();
    for (ChuckUGen src : srcs) {
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
  private volatile int visWriteIdx = 0;

  public float[] getVisBuffer() {
    float[] copy = new float[visBuffer.length];
    int len = visBuffer.length;
    int snapshotIdx = visWriteIdx; // Capture once to avoid race condition
    // Copy in two parts: from snapshotIdx to end, then from 0 to snapshotIdx
    System.arraycopy(visBuffer, snapshotIdx, copy, 0, len - snapshotIdx);
    System.arraycopy(visBuffer, 0, copy, len - snapshotIdx, snapshotIdx);
    return copy;
  }

  @Override
  public void tick(float[] buffer, int offset, int length, long systemTime) {
    if (systemTime != -1
        && systemTime == blockStartTime
        && blockCache != null
        && blockLength >= length) {
      if (buffer != null) {
        System.arraycopy(blockCache, 0, buffer, offset, length);
      }
      return;
    }

    if (blockCache == null || blockCache.length < length) blockCache = new float[length];
    java.util.Arrays.fill(blockCache, 0, length, 0.0f);

    // Vectorized summing from all sources
    java.util.List<ChuckUGen> srcs = getSources();
    for (ChuckUGen src : srcs) {
      float[] temp = new float[length];
      src.tick(temp, 0, length, systemTime);

      // SIMD Addition: blockCache += temp
      int i = 0;
      int bound = SPECIES.loopBound(length);
      for (; i < bound; i += SPECIES.length()) {
        FloatVector vSum = FloatVector.fromArray(SPECIES, blockCache, i);
        FloatVector vSrc = FloatVector.fromArray(SPECIES, temp, i);
        vSum.add(vSrc).intoArray(blockCache, i);
      }
      for (; i < length; i++) {
        blockCache[i] += temp[i];
      }
    }

    // Apply gain and write to output buffer
    for (int i = 0; i < length; i++) {
      float sample = blockCache[i] * gain;
      blockCache[i] = sample;
      if (buffer != null) buffer[offset + i] = sample;

      // Fill visualization buffer
      visBuffer[visWriteIdx] = sample;
      visWriteIdx = (visWriteIdx + 1) % visBuffer.length;
    }

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = systemTime + length - 1;
    if (length > 0) {
      lastOut = blockCache[length - 1];
    }
  }
}
