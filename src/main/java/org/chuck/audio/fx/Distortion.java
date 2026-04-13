package org.chuck.audio.fx;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/**
 * Distortion: A suite of non-linear saturation effects. Supports Overdrive, Fuzz, and Bitcrusher
 * modes.
 */
@doc("Saturation effect suite: Overdrive, Fuzz, and Bitcrusher.")
public class Distortion extends ChuckUGen {
  public static final int MODE_OVERDRIVE = 0;
  public static final int MODE_FUZZ = 1;
  public static final int MODE_BITCRUSHER = 2;

  private int mode = MODE_OVERDRIVE;
  private float drive = 1.0f; // 1.0 to 100.0+
  private int bits = 16;
  private int downsample = 1;
  private int downsampleCounter = 0;
  private float lastDownsampledValue = 0;

  @doc("Set distortion mode (0: Overdrive, 1: Fuzz, 2: Bitcrusher).")
  public void mode(int m) {
    this.mode = m;
  }

  @doc("Set drive/gain factor.")
  public void drive(float d) {
    this.drive = Math.max(1.0f, d);
  }

  @doc("Set bit depth for Bitcrusher (1 to 16).")
  public void bits(int b) {
    this.bits = Math.max(1, Math.min(16, b));
  }

  @doc("Set downsampling factor for Bitcrusher (1 = none).")
  public void downsample(int d) {
    this.downsample = Math.max(1, d);
  }

  @Override
  protected float compute(float input, long systemTime) {
    float x = input * drive;

    switch (mode) {
      case MODE_OVERDRIVE -> {
        // Soft clipping: tanh-like curve
        return (float) (Math.atan(x) * (2.0 / Math.PI)) * gain;
      }
      case MODE_FUZZ -> {
        // Hard clipping
        return Math.max(-1.0f, Math.min(1.0f, x)) * gain;
      }
      case MODE_BITCRUSHER -> {
        // Sample rate reduction
        if (++downsampleCounter >= downsample) {
          downsampleCounter = 0;
          // Bit depth reduction (quantization)
          float levels = (float) Math.pow(2, bits);
          lastDownsampledValue = Math.round(input * (levels / 2.0f)) / (levels / 2.0f);
        }
        return lastDownsampledValue * gain;
      }
      default -> {
        return input * gain;
      }
    }
  }
}
