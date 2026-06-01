package org.chuck.audio.stk.util;

/**
 * STK FormSwep — a BiQuad resonance filter that sweeps its center frequency/radius/gain toward a
 * target over time (ugen_stk.cpp). Used by Moog for its filter sweep. Plain DSP object.
 */
public final class StkFormSwep extends StkBiQuad {
  private double frequency = 0.0;
  private double radius = 0.0;
  private double targetGain = 1.0;
  private double targetFrequency = 0.0;
  private double targetRadius = 0.0;
  private double deltaGain = 0.0;
  private double deltaFrequency = 0.0;
  private double deltaRadius = 0.0;
  private double startFrequency = 0.0;
  private double startRadius = 0.0;
  private double startGain = 0.0;
  private double sweepState = 0.0;
  private double sweepRate = 0.002;
  private boolean dirty = false;

  public StkFormSwep(double sampleRate) {
    super(sampleRate);
    clear();
  }

  public void setStates(double aFrequency, double aRadius) {
    setStates(aFrequency, aRadius, 1.0);
  }

  public void setTargets(double aFrequency, double aRadius) {
    setTargets(aFrequency, aRadius, 1.0);
  }

  public void setStates(double aFrequency, double aRadius, double aGain) {
    dirty = false;
    if (frequency != aFrequency || radius != aRadius) setResonance(aFrequency, aRadius, true);
    frequency = aFrequency;
    radius = aRadius;
    gain = aGain;
    targetFrequency = aFrequency;
    targetRadius = aRadius;
    targetGain = aGain;
  }

  public void setTargets(double aFrequency, double aRadius, double aGain) {
    dirty = true;
    startFrequency = frequency;
    startRadius = radius;
    startGain = gain;
    targetFrequency = aFrequency;
    targetRadius = aRadius;
    targetGain = aGain;
    deltaFrequency = aFrequency - frequency;
    deltaRadius = aRadius - radius;
    deltaGain = aGain - gain;
    sweepState = 0.0;
  }

  public void setSweepRate(double aRate) {
    sweepRate = Math.max(0.0, Math.min(1.0, aRate));
  }

  @Override
  public double tick(double sample) {
    if (dirty) {
      sweepState += sweepRate;
      if (sweepState >= 1.0) {
        sweepState = 1.0;
        dirty = false;
        radius = targetRadius;
        frequency = targetFrequency;
        gain = targetGain;
      } else {
        radius = startRadius + (deltaRadius * sweepState);
        frequency = startFrequency + (deltaFrequency * sweepState);
        gain = startGain + (deltaGain * sweepState);
      }
      setResonance(frequency, radius, true);
    }
    return super.tick(sample);
  }
}
