package org.chuck.audio.osc;

import org.chuck.audio.ChuckUGen;

/** A white noise generator UGen. */
public class Noise extends ChuckUGen {

  public Noise() {
    this(true);
  }

  public Noise(boolean autoRegister) {
    super(autoRegister);
  }

  @Override
  protected float compute(float input, long systemTime) {
    // Generate a random float between -1.0 and 1.0 using bit-exact MT19937
    return input + (float) org.chuck.core.Std.rand2f(-1.0, 1.0);
  }
}
