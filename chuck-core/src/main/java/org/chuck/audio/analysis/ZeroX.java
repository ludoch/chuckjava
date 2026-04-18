package org.chuck.audio.analysis;

import org.chuck.audio.ChuckUGen;

/**
 * ZeroX: Zero-crossing detector. Outputs a single pulse (1.0) when the input signal crosses zero.
 */
public class ZeroX extends ChuckUGen {
  private float lastInput = 0.0f;

  @Override
  protected float compute(float input, long systemTime) {
    float out = 0.0f;
    if ((lastInput <= 0 && input > 0) || (lastInput >= 0 && input < 0)) {
      out = 1.0f;
    }
    lastInput = input;
    return out;
  }
}
