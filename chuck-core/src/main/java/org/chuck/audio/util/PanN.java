package org.chuck.audio.util;

/** PanN: N-channel panner. */
public class PanN extends MultiChannelUGen {
  private float pan = 0.0f; // -1 to 1

  public PanN(int numChannels) {
    super(numChannels);
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
    int n = lastOutChannels.length;
    if (n <= 0) return;

    float p = (pan + 1.0f) * (n - 1) / 2.0f;
    int i0 = (int) Math.floor(p);
    int i1 = i0 + 1;
    float frac = p - i0;

    for (int i = 0; i < n; i++) lastOutChannels[i] = 0;

    if (i0 >= 0 && i0 < n) lastOutChannels[i0] = input * (1.0f - frac);
    if (i1 >= 0 && i1 < n) lastOutChannels[i1] = input * frac;
  }
}
