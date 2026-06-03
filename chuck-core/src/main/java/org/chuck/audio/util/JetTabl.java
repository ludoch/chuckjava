package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;

/**
 * JetTabl — jet table lookup UGen used internally by Flute. Implements STK's non-linear jet "table
 * lookup" via the polynomial output = input * (input^2 - 1), saturated to [-1, 1].
 */
public class JetTabl extends ChuckUGen {

  public JetTabl() {
    super();
  }

  public JetTabl(boolean autoRegister) {
    super(autoRegister);
  }

  @Override
  protected float compute(float input, long systemTime) {
    // STK JetTabl::tick — polynomial approximation of the jet sigmoid, clamped to +/-1.
    double x = input;
    double out = x * (x * x - 1.0);
    if (out > 1.0) out = 1.0;
    if (out < -1.0) out = -1.0;
    return (float) out;
  }
}
