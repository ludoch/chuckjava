package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;

/** FullRect: Full-wave signal rectifier. */
public class FullRect extends ChuckUGen {
  @Override
  protected float compute(float input, long systemTime) {
    return Math.abs(input);
  }
}
