package org.chuck.audio.filter;

import org.chuck.audio.ChuckUGen;

/**
 * One-pole, one-zero filter. H(z) = (b0 + b1*z^-1) / (1 + a1*z^-1)
 *
 * <p>Convenience methods: setAllpass(coeff) for unity-gain allpass, setBlockZero(pole) for DC
 * blocker.
 */
public class PoleZero extends ChuckUGen {
  private double b0 = 1.0;
  private double b1 = 0.0;
  private double a1 = 0.0;

  private double lastInput = 0.0;
  private double lastOutput = 0.0;

  /** Unity-gain allpass: b0=coeff, b1=1, a1=coeff */
  public void setAllpass(double coeff) {
    b0 = coeff;
    b1 = 1.0;
    a1 = coeff;
  }

  /**
   * DC blocking filter: b0=1, b1=-1, a1=-pole (typically pole ≈ 0.99). High-passes with very low
   * cutoff.
   */
  public void setBlockZero(double pole) {
    b0 = 1.0;
    b1 = -1.0;
    a1 = -pole;
  }

  // Raw coefficient setters
  public void setB0(double v) {
    b0 = v;
  }

  public void setB1(double v) {
    b1 = v;
  }

  public void setA1(double v) {
    a1 = v;
  }

  @Override
  protected float compute(float input, long systemTime) {
    double y = b0 * input + b1 * lastInput - a1 * lastOutput;
    lastInput = input;
    lastOutput = y;
    return (float) y;
  }
}
