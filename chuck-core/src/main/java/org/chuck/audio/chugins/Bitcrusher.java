package org.chuck.audio.chugins;

import org.chuck.audio.ChuckUGen;

/**
 * Bitcrusher — applies aliased downsampling and bit-depth reduction.
 *
 * <p>Port of the chugins/Bitcrusher chugin by Spencer Salazar.
 */
public class Bitcrusher extends ChuckUGen {
  private int bits = 32;
  private int downsampleFactor = 1;
  private int sampleCount = 0;
  private float held = 0f;

  @Override
  protected float compute(float input, long systemTime) {
    float sample;
    if (sampleCount % downsampleFactor == 0) {
      float clamped = Math.max(-1f, Math.min(1f, input));
      held = clamped;
    }
    sample = held;
    sampleCount = (sampleCount + 1) % downsampleFactor;

    // Bit reduction
    int shift = 32 - bits;
    int q32 = (int) ((double) sample * Integer.MAX_VALUE);
    q32 = (q32 >> shift) << shift;
    return q32 / (float) Integer.MAX_VALUE;
  }

  public int bits(int b) {
    this.bits = Math.max(1, Math.min(32, b));
    return this.bits;
  }

  public int bits() {
    return bits;
  }

  public int downsampleFactor(int f) {
    this.downsampleFactor = Math.max(1, f);
    return this.downsampleFactor;
  }

  public int downsampleFactor() {
    return downsampleFactor;
  }

  public int downsample(int f) {
    return downsampleFactor(f);
  }

  public int downsample() {
    return downsampleFactor;
  }
}
