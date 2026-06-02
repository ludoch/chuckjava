package org.chuck.audio.stk.fm;

/**
 * Minimal port of STK's TwoZero filter (ugen_stk.cpp) as used by the FM voices: y[n] =
 * gain·(b0·x[n] + b1·x[n-1] + b2·x[n-2]). Plain DSP object; the FM voice configures b2 = -1 and a
 * small gain and feeds it the operator-3 output, using {@link #lastOut()} as a phase-modulation
 * offset.
 */
public final class StkTwoZero {
  private final double[] b = {1.0, 0.0, 0.0};
  private final double[] inputs = {0.0, 0.0, 0.0};
  private double output = 0.0;
  private double gain = 1.0;

  public void setB2(double b2) {
    b[2] = b2;
  }

  public void setGain(double g) {
    gain = g;
  }

  public double tick(double sample) {
    inputs[0] = gain * sample;
    output = b[2] * inputs[2] + b[1] * inputs[1] + b[0] * inputs[0];
    inputs[2] = inputs[1];
    inputs[1] = inputs[0];
    return output;
  }

  public double lastOut() {
    return output;
  }
}
