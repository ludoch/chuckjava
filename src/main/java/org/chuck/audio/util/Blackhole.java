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
    for (org.chuck.audio.ChuckUGen src : sources) {
      float[] temp = new float[length];
      src.tick(temp, 0, length, systemTime);
    }
    // Discard result - do not write to buffer (buffer might be null from ChuckVM fast path)
  }
}
