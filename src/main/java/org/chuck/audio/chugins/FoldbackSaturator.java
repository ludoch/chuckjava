package org.chuck.audio.chugins;

import org.chuck.audio.ChuckUGen;

/**
 * FoldbackSaturator — inverts a signal back against a threshold, creating rich harmonics.
 *
 * <p>Port of the chugins/FoldbackSaturator chugin.
 */
public class FoldbackSaturator extends ChuckUGen {
  private float makeupGain = 1.0f;
  private float threshold = 0.6f;
  private float index = 2.0f;

  @Override
  protected float compute(float input, long systemTime) {
    float s = input;
    if (s > threshold || s < -threshold) {
      s =
          (float)
              (Math.abs(
                      Math.abs((s - threshold) % (threshold * 4.0f) - threshold * 2.0f)
                          - threshold * 2.0f)
                  - threshold);
    }
    return s * (1.0f / threshold) * makeupGain;
  }

  public float makeupGain(float v) {
    makeupGain = v;
    return v;
  }

  public float makeupGain() {
    return makeupGain;
  }

  public float threshold(float v) {
    if (v >= 0) threshold = v;
    return threshold;
  }

  public float threshold() {
    return threshold;
  }

  public float index(float v) {
    index = v;
    return v;
  }

  public float index() {
    return index;
  }
}
