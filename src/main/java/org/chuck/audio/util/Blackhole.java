package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;

/** A blackhole UGen. Ticks all its inputs but discards the output. */
public class Blackhole extends ChuckUGen {
  @Override
  protected float compute(float input, long systemTime) {
    return 0.0f;
  }

  @Override
  public void tick(float[] buffer, int offset, int length, long systemTime) {
    // Pull audio from all sources to keep them ticking
    java.util.List<org.chuck.audio.ChuckUGen> srcs = getSources();
    for (org.chuck.audio.ChuckUGen src : srcs) {
      float[] temp = new float[length];
      src.tick(temp, 0, length, systemTime);
    }
    // Discard result
  }
}
