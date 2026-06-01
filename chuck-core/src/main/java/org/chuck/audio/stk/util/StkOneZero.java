package org.chuck.audio.stk.util;

/**
 * STK OneZero filter (ugen_stk.cpp), default coefficients b0=b1=0.5 (a simple two-point averager) as
 * used by PluckTwo's loop filters. Plain DSP object, double precision.
 */
public final class StkOneZero {
  private final double[] b = {0.5, 0.5};
  private double in1 = 0.0;
  private double output0 = 0.0;
  private double gain = 1.0;

  public void setZero(double theZero) {
    if (theZero > 0.0) b[0] = 1.0 / (1.0 + theZero);
    else b[0] = 1.0 / (1.0 - theZero);
    b[1] = -theZero * b[0];
  }

  public void setGain(double g) {
    gain = g;
  }

  public void clear() {
    in1 = 0.0;
    output0 = 0.0;
  }

  public double tick(double sample) {
    double in0 = gain * sample;
    output0 = b[1] * in1 + b[0] * in0;
    in1 = in0;
    return output0;
  }

  public double lastOut() {
    return output0;
  }
}
