package org.chuck.audio.stk.util;

/**
 * STK allpass-interpolating delay line (DelayA), ported verbatim from ugen_stk.cpp. Plain DSP object
 * ticked directly by the waveguide instruments. Double precision to match STK's MY_FLOAT.
 */
public final class StkDelayA {
  private final double[] inputs;
  private final int length;
  private int inPoint = 0;
  private int outPoint = 0;
  private double delay;
  private double alpha;
  private double coeff;
  private double apInput = 0.0;
  private double nextOutput = 0.0;
  private double output0 = 0.0;
  private boolean doNextOut = true;

  public StkDelayA(double theDelay, int maxDelay) {
    length = maxDelay + 1;
    inputs = new double[length];
    inPoint = 0;
    setDelay(theDelay);
    apInput = 0.0;
    doNextOut = true;
  }

  public void clear() {
    for (int i = 0; i < length; i++) inputs[i] = 0.0;
    output0 = 0.0;
    apInput = 0.0;
  }

  public void setDelay(double theDelay) {
    double outPointer;
    if (theDelay > length - 1) {
      outPointer = inPoint + 1.0;
      delay = length - 1;
    } else if (theDelay < 0.5) {
      outPointer = inPoint + 0.4999999999;
      delay = 0.5;
    } else {
      outPointer = inPoint - theDelay + 1.0;
      delay = theDelay;
    }
    if (outPointer < 0) outPointer += length;
    outPoint = (int) outPointer;
    alpha = 1.0 + outPoint - outPointer;
    if (alpha < 0.5) {
      outPoint += 1;
      if (outPoint >= length) outPoint -= length;
      alpha += 1.0;
    }
    coeff = (1.0 - alpha) / (1.0 + alpha);
  }

  public double nextOut() {
    if (doNextOut) {
      nextOutput = -coeff * output0;
      nextOutput += apInput + (coeff * inputs[outPoint]);
      doNextOut = false;
    }
    return nextOutput;
  }

  public double tick(double sample) {
    inputs[inPoint++] = sample;
    if (inPoint == length) inPoint -= length;
    output0 = nextOut();
    doNextOut = true;
    apInput = inputs[outPoint++];
    if (outPoint == length) outPoint -= length;
    return output0;
  }

  public double lastOut() {
    return output0;
  }
}
