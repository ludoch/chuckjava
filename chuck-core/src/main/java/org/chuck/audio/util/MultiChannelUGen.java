package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.ChuckType;

/** Base class for multi-channel Unit Generators. Maintains an array of output values. */
public abstract class MultiChannelUGen extends ChuckUGen {
  protected float[] lastOutChannels;
  protected ChuckUGen[] channelProxies;

  public MultiChannelUGen(int numChannels) {
    super(new ChuckType("MultiChannelUGen", ChuckType.OBJECT, 0, 0));
    this.numOutputs = numChannels;
    this.lastOutChannels = new float[numChannels];
    this.channelProxies = new ChuckUGen[numChannels];
  }

  /** ChucK-style: ugen.chan(i) returns proxy for channel i */
  public ChuckUGen chan(int i) {
    if (i >= 0 && i < channelProxies.length) {
      if (channelProxies[i] == null) {
        channelProxies[i] = new ChannelProxy(this, i);
      }
      return channelProxies[i];
    }
    return null;
  }

  @Override
  public float getChannelLastOut(int i) {
    if (i >= 0 && i < lastOutChannels.length) return lastOutChannels[i];
    return 0.0f;
  }

  @Override
  public float getChannelLastOut(int i, long systemTime) {
    // If we haven't reached this time yet, we must tick first
    if (systemTime != -1 && systemTime > lastTickTime) {
      this.tick(systemTime);
    }
    return getChannelLastOut(i);
  }

  @Override
  public float tick(long systemTime) {
    // 1. Standard sample-caching check
    if (systemTime != -1 && systemTime == lastTickTime) {
      return lastOut;
    }

    // 2. Circular dependency protection
    if (isTicking) return lastOut;
    isTicking = true;

    try {
      // 3. Sum inputs channel-wise from all sources
      float[] sums = new float[lastOutChannels.length];
      final ChuckUGen[] sources = this.sourcesArray;
      final int count = this.sourcesCount;
      for (int s = 0; s < count; s++) {
        ChuckUGen src = sources[s];
        if (src != null) {
          src.tick(systemTime);
          for (int i = 0; i < sums.length; i++) {
            sums[i] += src.getChannelLastOut(i, systemTime);
          }
        }
      }

      // 4. Compute multi-channel samples
      computeMulti(sums, systemTime);

      // 5. Apply gain to all channels and set master lastOut
      for (int i = 0; i < lastOutChannels.length; i++) {
        lastOutChannels[i] *= gain;
        if (Math.abs(lastOutChannels[i]) < 1.0e-15f) lastOutChannels[i] = 0.0f;
      }
      lastOut = lastOutChannels.length > 0 ? lastOutChannels[0] : 0.0f;

      // 6. Mark as processed for this timestep
      lastTickTime = systemTime;
      blockStartTime = systemTime;
      blockLength = 0;

      return lastOut;
    } finally {
      isTicking = false;
    }
  }

  @Override
  protected float compute(float input, long systemTime) {
    // Legacy single-input fallback
    float[] inputs = new float[lastOutChannels.length];
    java.util.Arrays.fill(inputs, input);
    computeMulti(inputs, systemTime);
    return lastOutChannels.length > 0 ? lastOutChannels[0] : 0.0f;
  }

  /** Subclasses should implement this to fill lastOutChannels based on multi-channel input. */
  protected void computeMulti(float[] inputs, long systemTime) {
    // Default fallback to legacy single-float computeMulti
    float sum = 0;
    for (float f : inputs) sum += f;
    // Standard ChucK behavior when summing multi-channel input to mono?
    // Usually it's just a sum.
    computeMulti(sum, systemTime);
  }

  /** Legacy mono-input compute. Subclasses should migrate to the array version. */
  protected abstract void computeMulti(float input, long systemTime);

  @Override
  public ChuckUGen getOutputChannel(int i) {
    return this;
  }
}
