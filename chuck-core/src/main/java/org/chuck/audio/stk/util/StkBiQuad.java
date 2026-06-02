package org.chuck.audio.stk.util;

/**
 * STK BiQuad filter (ugen_stk.cpp) — two-pole/two-zero, with the resonance helper used by FormSwep.
 * Plain DSP object, double precision. Subclassed by {@link StkFormSwep}.
 */
public class StkBiQuad {
  protected final double[] b = {1.0, 0.0, 0.0};
  protected final double[] a = {1.0, 0.0, 0.0};
  protected final double[] inputs = {0.0, 0.0, 0.0};
  protected final double[] outputs = {0.0, 0.0, 0.0};
  protected double gain = 1.0;
  private final double sampleRate;

  public StkBiQuad(double sampleRate) {
    this.sampleRate = sampleRate;
  }

  public void clear() {
    inputs[0] = inputs[1] = inputs[2] = 0.0;
    outputs[0] = outputs[1] = outputs[2] = 0.0;
  }

  /**
   * BiQuad::setResonance — resonant poles at (frequency, radius); normalize peak gain via zeros.
   */
  public void setResonance(double frequency, double radius, boolean normalize) {
    a[2] = radius * radius;
    a[1] = -2.0 * radius * Math.cos(2.0 * Math.PI * frequency / sampleRate);
    if (normalize) {
      b[0] = 0.5 - 0.5 * a[2];
      b[1] = 0.0;
      b[2] = -b[0];
    }
  }

  public double tick(double sample) {
    inputs[0] = gain * sample;
    outputs[0] = b[0] * inputs[0] + b[1] * inputs[1] + b[2] * inputs[2];
    outputs[0] -= a[2] * outputs[2] + a[1] * outputs[1];
    inputs[2] = inputs[1];
    inputs[1] = inputs[0];
    outputs[2] = outputs[1];
    outputs[1] = outputs[0];
    return outputs[0];
  }
}
