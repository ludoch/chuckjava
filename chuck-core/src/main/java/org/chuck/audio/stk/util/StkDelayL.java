package org.chuck.audio.stk.util;

/**
 * STK linear-interpolating delay line (DelayL), ported verbatim from ugen_stk.cpp. Plain DSP object;
 * used as the comb delay in PluckTwo. Double precision to match STK's MY_FLOAT.
 */
public final class StkDelayL {
  private final double[] inputs;
  private final int length;
  private int inPoint = 0;
  private int outPoint = 0;
  private double delay;
  private double alpha = 0.0;
  private double omAlpha = 0.0;
  private double nextOutput = 0.0;
  private double output0 = 0.0;
  private boolean doNextOut = true;

  public StkDelayL(double theDelay, int maxDelay) {
    length = maxDelay + 1;
    inputs = new double[length];
    inPoint = 0;
    setDelay(theDelay);
    doNextOut = true;
  }

  public void clear() {
    for (int i = 0; i < length; i++) inputs[i] = 0.0;
    output0 = 0.0;
  }

  public void setDelay(double theDelay) {
    double outPointer;
    if (theDelay > length - 1) {
      outPointer = inPoint + 1.0;
      delay = length - 1;
    } else if (theDelay < 0) {
      outPointer = inPoint;
      delay = 0;
    } else {
      outPointer = inPoint - theDelay;
      delay = theDelay;
    }
    while (outPointer < 0) outPointer += length;
    outPoint = (int) outPointer;
    alpha = outPointer - outPoint;
    omAlpha = 1.0 - alpha;
  }

  public double nextOut() {
    if (doNextOut) {
      nextOutput = inputs[outPoint] * omAlpha;
      if (outPoint + 1 < length) nextOutput += inputs[outPoint + 1] * alpha;
      else nextOutput += inputs[0] * alpha;
      doNextOut = false;
    }
    return nextOutput;
  }

  public double tick(double sample) {
    inputs[inPoint++] = sample;
    if (inPoint == length) inPoint -= length;
    output0 = nextOut();
    doNextOut = true;
    if (++outPoint >= length) outPoint -= length;
    return output0;
  }

  public double lastOut() {
    return output0;
  }
}
