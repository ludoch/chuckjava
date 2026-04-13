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

  /**
   * New method to support bit-exact multi-channel block caching. Overridden by subclasses that
   * manage multi-channel block caches (like Pan2).
   */
  public float getChannelLastOut(int i, long systemTime) {
    // Default implementation: check if parent's unified block cache contains the sample.
    // If systemTime matches the current state, just return it.
    if (systemTime == lastTickTime) {
      return getChannelLastOut(i);
    }
    // Fallback: if it's in the block window but not exactly lastTickTime,
    // it depends on whether the subclass has a multi-channel block cache.
    return getChannelLastOut(i);
  }

  @Override
  public float tick(long systemTime) {
    if (systemTime != -1 && systemTime == lastTickTime) {
      return lastOut;
    }

    if (isTicking) return lastOut;
    isTicking = true;

    try {
      float sum = 0.0f;
      java.util.List<ChuckUGen> srcs = getSources();
      for (ChuckUGen src : srcs) {
        sum += src.tick(systemTime);
      }

      computeMulti(sum, systemTime);

      // Apply gain to all channels
      for (int i = 0; i < lastOutChannels.length; i++) {
        lastOutChannels[i] *= gain;
      }

      // lastOut is usually channel 0 or the average
      lastOut = lastOutChannels.length > 0 ? lastOutChannels[0] : 0.0f;

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
