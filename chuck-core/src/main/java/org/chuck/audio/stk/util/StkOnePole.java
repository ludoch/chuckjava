package org.chuck.audio.stk.util;

/**
 * STK OnePole filter (ugen_stk.cpp), default b0=0.1, a1=-0.9 — used by the Sampler's output filter.
 * Plain DSP object, double precision.
 */
public final class StkOnePole {
  private double b0 = 0.1;
  private double a1 = -0.9;
  private double out1 = 0.0;
  private double gain = 1.0;

  public void setPole(double thePole) {
    b0 = (thePole > 0.0) ? (1.0 - thePole) : (1.0 + thePole);
    a1 = -thePole;
  }

  public void setGain(double g) {
    gain = g;
  }

  public void clear() {
    out1 = 0.0;
  }

  public double tick(double sample) {
    double out0 = b0 * gain * sample - a1 * out1;
    out1 = out0;
    return out0;
  }
}
