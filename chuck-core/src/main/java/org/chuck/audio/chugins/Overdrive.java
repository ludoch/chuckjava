package org.chuck.audio.chugins;

import org.chuck.audio.ChuckUGen;

/**
 * Overdrive — soft-clipping distortion with power-law shaping.
 *
 * <p>Port of the chugins/Overdrive chugin. drive &gt; 1 compresses peaks; drive &lt; 1 expands.
 */
public class Overdrive extends ChuckUGen {
  private float drive = 1.0f;

  @Override
  protected float compute(float input, long systemTime) {
    if (drive == 1.0f) return input;
    if (input >= 1.0f) return 1.0f;
    if (input > 0.0f) return (float) (1.0 - Math.pow(1.0 - input, drive));
    if (input >= -1.0f) return (float) (Math.pow(1.0 + input, drive) - 1.0);
    return -1.0f;
  }

  public float drive(float d) {
    if (d >= 0) drive = d;
    return drive;
  }

  public float drive() {
    return drive;
  }
}
