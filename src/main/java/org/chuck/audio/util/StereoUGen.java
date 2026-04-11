package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;

/** Base class for stereo Unit Generators. */
public abstract class StereoUGen extends MultiChannelUGen {
  public StereoUGen() {
    super(2);
  }

  public float getLastOutLeft() {
    return lastOutChannels[0];
  }

  public float getLastOutRight() {
    return lastOutChannels[1];
  }

  public ChuckUGen left() {
    return chan(0);
  }

  public ChuckUGen right() {
    return chan(1);
  }

  @Override
  protected void computeMulti(float input, long systemTime) {
    computeStereo(input, systemTime);
  }

  protected abstract void computeStereo(float input, long systemTime);
}
