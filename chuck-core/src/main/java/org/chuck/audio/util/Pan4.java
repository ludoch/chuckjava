package org.chuck.audio.util;

/** Pan4: 4-channel panner. Puts input into one of 4 channels based on pan (-1 to 1). */
public class Pan4 extends MultiChannelUGen {
  private float pan = 0.0f;

  public Pan4() {
    super(4);
  }

  public float pan(float p) {
    this.pan = p;
    return p;
  }

  public float pan() {
    return pan;
  }

  @Override
  protected void computeMulti(float input, long systemTime) {
    // Map pan -1..1 to 0..3
    float p = (pan + 1.0f) * 1.5f;
    int i0 = (int) Math.floor(p);
    int i1 = i0 + 1;
    float frac = p - i0;

    for (int i = 0; i < 4; i++) lastOutChannels[i] = 0;

    if (i0 >= 0 && i0 < 4) lastOutChannels[i0] = input * (1.0f - frac);
    if (i1 >= 0 && i1 < 4) lastOutChannels[i1] = input * frac;
  }
}
