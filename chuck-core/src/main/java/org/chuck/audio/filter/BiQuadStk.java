package org.chuck.audio.filter;

/**
 * BiQuadStk — STK-style BiQuad filter (second-order IIR). Coefficients set via b0/b1/b2
 * (feedforward) and a1/a2 (feedback). Distinct from BiQuad (which uses prad/pfreq API).
 */
public class BiQuadStk extends FilterStk {
  private double b0 = 1, b1 = 0, b2 = 0;
  private double a1 = 0, a2 = 0;
  private double x1 = 0, x2 = 0;
  private double y1 = 0, y2 = 0;

  public BiQuadStk(float sampleRate) {
    super(sampleRate);
  }

  public void setB0(double v) {
    b0 = v;
  }

  public void setB1(double v) {
    b1 = v;
  }

  public void setB2(double v) {
    b2 = v;
  }

  public void setA1(double v) {
    a1 = v;
  }

  public void setA2(double v) {
    a2 = v;
  }

  public void setCoeffs(double _b0, double _b1, double _b2, double _a1, double _a2) {
    b0 = _b0;
    b1 = _b1;
    b2 = _b2;
    a1 = _a1;
    a2 = _a2;
  }

  @Override
  public void clear() {
    x1 = x2 = y1 = y2 = 0;
  }

  @Override
  protected float compute(float input, long systemTime) {
    double x0 = input * gain;
    double y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
    x2 = x1;
    x1 = x0;
    y2 = y1;
    y1 = y0;
    return (float) y0;
  }
}
