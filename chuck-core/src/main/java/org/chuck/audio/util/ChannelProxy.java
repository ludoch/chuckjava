package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.ChuckType;

/**
 * A proxy UGen representing a single channel of a MultiChannelUGen. Ticking this proxy returns the
 * sample from the parent's channel.
 */
public class ChannelProxy extends ChuckUGen {
  private final MultiChannelUGen parent;
  private final int channelIndex;

  public ChannelProxy(MultiChannelUGen parent, int channelIndex) {
    super(new ChuckType("ChannelProxy", ChuckType.OBJECT, 0, 0));
    this.parent = parent;
    this.channelIndex = channelIndex;
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

    // Ensure parent is ticked for this sample
    parent.tick(systemTime);
    lastOut = parent.getChannelLastOut(channelIndex);
    lastTickTime = systemTime;
    blockStartTime = systemTime;
    blockLength = 0;
    return lastOut;
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

    // Ensure parent is ticked for this block
    parent.tick(null, 0, length, systemTime);

    if (blockCache == null || blockCache.length < length) blockCache = new float[length];

    for (int i = 0; i < length; i++) {
      // Access pre-computed samples from parent's channel logic or its own block cache
      // Since MultiChannelUGen might not have a unified block cache, we use getChannelLastOut
      // But wait! getChannelLastOut usually returns a scalar for a specific time.
      // We need a vectorized way to get a channel's block.
      // For now, we'll pull scalars from parent (parent will hit its own cache)
      float val = parent.getChannelLastOut(channelIndex, systemTime + i);
      blockCache[i] = val;
      if (buffer != null) buffer[offset + i] = val;
    }

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = systemTime + length - 1;
    lastOut = blockCache[length - 1];
  }

  @Override
  protected float compute(float input, long systemTime) {
    return parent.getChannelLastOut(channelIndex);
  }
}
