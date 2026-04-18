package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.filter.OneZero;
import org.chuck.audio.fx.DelayL;
import org.chuck.audio.util.Impulse;

/** A basic plucked string physical model using Karplus-Strong synthesis. */
public class Plucked extends ChuckUGen {
  protected final DelayL delayLine;
  protected final OneZero loopFilter;
  protected final Impulse excitation;
  protected final float sampleRate;
  protected float baseFreq = 440.0f;

  public Plucked(float lowestFrequency, float sampleRate) {
    this.sampleRate = sampleRate;
    int length = (int) (sampleRate / lowestFrequency + 1);
    this.delayLine = new DelayL(length);
    this.loopFilter = new OneZero();
    this.excitation = new Impulse();

    loopFilter.setB0(0.5f);
    loopFilter.setB1(0.5f);
  }

  public void setFreq(double frequency) {
    this.baseFreq = (float) frequency;
    double delay = sampleRate / frequency - 0.5;
    delayLine.setDelay(delay);
  }

  @Override
  public void setData(int index, long value) {
    if (index == 0) { // freq
      setFreq(Double.longBitsToDouble(value));
    }
    super.setDataInternal(index, value);
    triggerDataHook(index, value);
  }

  public void noteOn(float velocity) {
    excitation.setNext(velocity);
  }

  @Override
  protected float compute(float input, long systemTime) {
    float loopIn = delayLine.getLastOut();
    float filtered = loopFilter.tick(loopIn, systemTime);
    float out = delayLine.tick(excitation.tick(systemTime, systemTime) + filtered, systemTime);
    return out;
  }
}
