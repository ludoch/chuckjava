package org.chuck.audio.osc;

import java.util.List;
import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/**
 * A sine wave oscillator. To match native ChucK, this uses Math.sin and samples BEFORE increment.
 */
@doc("A sine wave oscillator.")
public class SinOsc extends Osc {
  public SinOsc() {
    super();
  }

  public SinOsc(float sampleRate) {
    this(sampleRate, true);
  }

  public SinOsc(float sampleRate, boolean autoRegister) {
    super(sampleRate, autoRegister);
  }

  @Override
  protected double computeOsc(double phase) {
    return Math.sin(phase * 2.0 * Math.PI);
  }

  @Override
  public void tick(float[] buffer, int offset, int length, long systemTime) {
    tick(buffer, offset, length, systemTime, null);
  }

  @Override
  public void tick(float[] buffer, int offset, int length, long systemTime, float[] manualInput) {
    if (systemTime != -1
        && systemTime == blockStartTime
        && blockCache != null
        && blockLength >= length) {
      if (buffer != null) System.arraycopy(blockCache, 0, buffer, offset, length);
      return;
    }
    if (blockCache == null || blockCache.length < length) blockCache = new float[length];

    // If we have sources (modulation), we must sum them first
    float[] inputSum = new float[length];
    if (manualInput != null) {
      System.arraycopy(manualInput, 0, inputSum, 0, length);
    }
    List<ChuckUGen> srcs = getSources();
    if (!srcs.isEmpty()) {
      for (ChuckUGen src : srcs) {
        float[] temp = new float[length];
        src.tick(temp, 0, length, systemTime);
        for (int j = 0; j < length; j++) inputSum[j] += temp[j];
      }
    }

    double f_phase = phase;
    int i = 0;
    // Scalar compute to ensure 100% parity with Math.sin() and correct sample timing
    for (; i < length; i++) {
      boolean inc_phase = true;
      double d_num = this.num;

      if (!srcs.isEmpty() || manualInput != null) {
        float in = inputSum[i];
        switch (sync) {
          case 0 -> {
            d_num = in / sampleRate;
            if (d_num >= 1.0) d_num -= Math.floor(d_num);
            else if (d_num <= -1.0) d_num += Math.floor(d_num);
          }
          case 1 -> {
            f_phase = in;
            inc_phase = false;
          }
          case 2 -> {
            d_num = (freq + in) / sampleRate;
            if (d_num >= 1.0) d_num -= Math.floor(d_num);
            else if (d_num <= -1.0) d_num += Math.floor(d_num);
          }
        }
      }

      // 1. Compute current sample based on current phase
      float out = (float) (Math.sin(f_phase * 2.0 * Math.PI) * gain);
      blockCache[i] = out;
      if (buffer != null) buffer[offset + i] = out;

      // 2. Advance phase
      if (inc_phase) {
        f_phase += d_num;
        if (f_phase >= 1.0 || f_phase < 0.0) f_phase -= Math.floor(f_phase);
      }
    }

    this.phase = f_phase;
    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = systemTime + length - 1;
    if (length > 0) {
      lastOut = blockCache[length - 1];
    }
  }
}
