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
  public float tick(long systemTime) {
    if (systemTime != -1 && systemTime == lastTickTime) {
      return lastOut;
    }

    if (isTicking) return lastOut;
    isTicking = true;

    try {
      float sum = 0.0f;
      for (ChuckUGen src : sources) {
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
    // If someone wants a specific channel output, we can't easily return a UGen proxy
    // without more complexity (like Pan2.left/right).
    // For now, we return this, and DacChannel/MultiChannelDac handle the indexing.
    return this;
  }
}
