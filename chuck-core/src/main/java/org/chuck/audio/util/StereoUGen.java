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
    // Use the already-summed mono input for stereo processing.
    // Call the 2-arg version so subclasses that override computeStereo(left, right, t)
    // (e.g. JCRev, Echo, NRev) are dispatched correctly.
    // Subclasses that only override computeStereo(float, long) are reached via the
    // default 2-arg fallback: computeStereo((left+right)*0.5f, t).
    computeStereo(input, input, systemTime);
  }

  /** Subclasses can override this for true stereo processing. */
  protected void computeStereo(float left, float right, long systemTime) {
    // Default fallback: call legacy mono-input compute if not overridden
    computeStereo((left + right) * 0.5f, systemTime);
  }

  /** Legacy mono-input stereo compute. Subclasses should migrate to the 2-arg version. */
  protected abstract void computeStereo(float input, long systemTime);
}
