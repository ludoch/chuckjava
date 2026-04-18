package org.chuck.audio.util;

/** Pan8: 8-channel panner. */
public class Pan8 extends MultiChannelUGen {
  private float pan = 0.0f;

  public Pan8() {
    super(8);
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
    float p = (pan + 1.0f) * 3.5f;
    int i0 = (int) Math.floor(p);
    int i1 = i0 + 1;
    float frac = p - i0;

    for (int i = 0; i < 8; i++) lastOutChannels[i] = 0;

    if (i0 >= 0 && i0 < 8) lastOutChannels[i0] = input * (1.0f - frac);
    if (i1 >= 0 && i1 < 8) lastOutChannels[i1] = input * frac;
  }
}
