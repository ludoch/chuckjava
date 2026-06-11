package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.stk.util.StkBiQuad;
import org.chuck.audio.stk.util.StkDelayA;
import org.chuck.audio.stk.util.StkDelayL;
import org.chuck.audio.stk.util.StkOneZero;
import org.chuck.core.ChuckVM;
import org.chuck.core.Std;

/** A stiff Karplus-Strong string physical model. */
public class StifKarp extends ChuckUGen {
  private final StkDelayA delayLine;
  private final StkDelayL combDelay;
  private final StkOneZero filter;
  private final StkBiQuad[] biquad = new StkBiQuad[4];

  private double loopGain;
  private double baseLoopGain;
  private double lastFrequency;
  private double lastLength;
  private double stretching;
  private double pluckAmplitude;
  private double pickupPosition;

  private final float sampleRate;

  public StifKarp() {
    this(ChuckVM.CURRENT_VM.get().getSampleRate());
  }

  public StifKarp(float sampleRate) {
    this.sampleRate = sampleRate;

    // lowest frequency = 8.0 Hz default to calculate max delay length
    int maxDelay = (int) (sampleRate / 8.0);
    delayLine = new StkDelayA(sampleRate / 220.0, maxDelay);
    combDelay = new StkDelayL(0.5 * 0.4 * (sampleRate / 220.0), maxDelay);
    filter = new StkOneZero();

    for (int i = 0; i < 4; i++) {
      biquad[i] = new StkBiQuad(sampleRate);
    }

    pluckAmplitude = 0.3;
    pickupPosition = 0.4;
    stretching = 0.9999;
    baseLoopGain = 0.995;
    loopGain = 0.999;

    clear();
    setFreq(220.0);
  }

  public void clear() {
    delayLine.clear();
    combDelay.clear();
    filter.clear();
    for (int i = 0; i < 4; i++) {
      biquad[i].clear();
    }
  }

  public void setFreq(double frequency) {
    if (frequency <= 0.0) return;

    lastFrequency = frequency;
    lastLength = sampleRate / lastFrequency;
    double delay = lastLength - 0.5;
    delayLine.setDelay(delay);

    loopGain = baseLoopGain + (frequency * 0.000005);
    if (loopGain >= 1.0) loopGain = 0.99999;

    setStretch((float) stretching);

    combDelay.setDelay(0.5 * pickupPosition * lastLength);
  }

  public void freq(float freq) {
    setFreq(freq);
  }

  public void setStretch(float stretch) {
    stretching = stretch;
    double coefficient;
    double freq = lastFrequency * 2.0;
    double dFreq = ((0.5 * sampleRate) - freq) * 0.25;
    double temp = 0.5 + (stretch * 0.5);
    if (temp > 0.99999) temp = 0.99999;
    for (int i = 0; i < 4; i++) {
      coefficient = temp * temp;
      biquad[i].setA2(coefficient);
      biquad[i].setB0(coefficient);
      biquad[i].setB2(1.0);

      coefficient = -2.0 * temp * Math.cos(2.0 * Math.PI * freq / sampleRate);
      biquad[i].setA1(coefficient);
      biquad[i].setB1(coefficient);

      freq += dFreq;
    }
  }

  public void stretch(float s) {
    setStretch(s);
  }

  public void setPickupPosition(float position) {
    if (position < 0.0 || position > 1.0) return;
    pickupPosition = position;
    combDelay.setDelay(0.5 * pickupPosition * lastLength);
  }

  public void pickupPos(float p) {
    setPickupPosition(p);
  }

  public void setBaseLoopGain(float aGain) {
    baseLoopGain = aGain;
    loopGain = baseLoopGain + (lastFrequency * 0.000005);
    if (loopGain > 0.99999) loopGain = 0.99999;
  }

  public void pluck(float amplitude) {
    if (amplitude < 0.0 || amplitude > 1.0) return;
    pluckAmplitude = amplitude;
    for (int i = 0; i < (int) lastLength; i++) {
      // Fill delay with noise additively with current contents
      delayLine.tick((delayLine.lastOut() * 0.6) + 0.4 * Std.rand2f(-1.0, 1.0) * pluckAmplitude);
    }
  }

  public void noteOn(float velocity) {
    pluck(velocity);
  }

  public void noteOn(float frequency, float amplitude) {
    setFreq(frequency);
    pluck(amplitude);
  }

  public void noteOff(float velocity) {
    if (velocity < 0.0 || velocity > 1.0) return;
    loopGain = (1.0 - velocity) * 0.5;
  }

  public void controlChange(int number, float value) {
    float normalizedValue = value * (1.0f / 128.0f);
    if (number == 4) {
      setPickupPosition(normalizedValue);
    } else if (number == 11) {
      setBaseLoopGain(0.97f + (normalizedValue * 0.03f));
    } else if (number == 1) {
      setStretch(0.9f + (0.1f * (1.0f - normalizedValue)));
    }
  }

  @Override
  protected float compute(float input, long systemTime) {
    double temp = delayLine.lastOut() * loopGain;

    // Calculate allpass stretching.
    for (int i = 0; i < 4; i++) {
      temp = biquad[i].tick(temp);
    }

    // Moving average filter.
    temp = filter.tick(temp);

    double delayOut = delayLine.tick(temp);
    double out = delayOut - combDelay.tick(delayOut);

    lastOut = (float) out;
    return lastOut;
  }
}
