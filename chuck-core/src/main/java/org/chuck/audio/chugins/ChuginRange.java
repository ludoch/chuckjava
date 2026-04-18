package org.chuck.audio.chugins;

import org.chuck.audio.ChuckUGen;

/**
 * ChuginRange — linear rescale of a signal from one range to another.
 *
 * <p>Maps input in [inMin, inMax] to output in [outMin, outMax]. Optionally hard-clips output to
 * [outMin, outMax].
 *
 * <p>Port of chugins/Range.
 *
 * <p>Named ChuginRange to avoid collision with java.util.stream. Use as "Range" in ChucK scripts.
 */
public class ChuginRange extends ChuckUGen {
  private float inMin = -1.0f;
  private float inMax = 1.0f;
  private float outMin = 0.0f;
  private float outMax = 1.0f;
  private boolean clip = false;

  @Override
  protected float compute(float input, long systemTime) {
    float out = (input - inMin) / (inMax - inMin);
    out = out * (outMax - outMin) + outMin;
    if (clip) {
      if (out < outMin) out = outMin;
      if (out > outMax) out = outMax;
    }
    return out;
  }

  public void setInRange(float min, float max) {
    inMin = min;
    inMax = max;
  }

  public void setOutRange(float min, float max) {
    outMin = min;
    outMax = max;
  }

  public void setRange(float in0, float in1, float out0, float out1) {
    inMin = in0;
    inMax = in1;
    outMin = out0;
    outMax = out1;
  }

  public float inMin(float v) {
    inMin = v;
    return v;
  }

  public float inMin() {
    return inMin;
  }

  public float inMax(float v) {
    inMax = v;
    return v;
  }

  public float inMax() {
    return inMax;
  }

  public float outMin(float v) {
    outMin = v;
    return v;
  }

  public float outMin() {
    return outMin;
  }

  public float outMax(float v) {
    outMax = v;
    return v;
  }

  public float outMax() {
    return outMax;
  }

  public int clip(int v) {
    clip = v != 0;
    return v;
  }

  public int clip() {
    return clip ? 1 : 0;
  }
}
