package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;

/** HalfRect: Half-wave signal rectifier. */
public class HalfRect extends ChuckUGen {
  @Override
  protected float compute(float input, long systemTime) {
    return Math.max(0.0f, input);
  }
}
