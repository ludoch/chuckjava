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
      // 3. Sum inputs from mono sources
      float sum = 0.0f;
      java.util.List<ChuckUGen> srcs = getSources();
      for (ChuckUGen src : srcs) {
        sum += src.tick(systemTime);
      }

      // 4. Compute multi-channel samples
      computeMulti(sum, systemTime);

      // 5. Apply gain to all channels and set master lastOut
      for (int i = 0; i < lastOutChannels.length; i++) {
        lastOutChannels[i] *= gain;
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
    computeMulti(input, systemTime);
    return lastOutChannels.length > 0 ? lastOutChannels[0] : 0.0f;
  }

  /** Subclasses implement this to fill lastOutChannels based on input. */
  protected abstract void computeMulti(float input, long systemTime);

  @Override
  public ChuckUGen getOutputChannel(int i) {
    return this;
  }
}
