package org.chuck.audio.fx;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.osc.SinOsc;

/** A chorus effect. */
public class Chorus extends ChuckUGen {
  private final DelayL delayLine;
  private final SinOsc lfo;
  private float modDepth = 0.05f;
  private float baseDelaySamples;
  private float mix = 0.5f;

  public Chorus(float sampleRate) {
    // Base delay of 30ms is common for chorus
    this.baseDelaySamples = 0.030f * sampleRate;
    this.delayLine = new DelayL((int) (baseDelaySamples * 2));
    this.lfo = new SinOsc(sampleRate);
    this.lfo.setFreq(0.25); // Default modulation frequency
  }

  public void setModFreq(double freq) {
    lfo.setFreq(freq);
  }

  public void setModDepth(float depth) {
    this.modDepth = depth;
  }

  public void setMix(float mix) {
    this.mix = mix;
  }

  @Override
  protected float compute(float input, long systemTime) {
    // LFO outputs -1 to 1, we want to oscillate around baseDelay
    float lfoOut = lfo.tick(systemTime, systemTime);
    double currentDelay = baseDelaySamples * (1.0 + modDepth * lfoOut);
    delayLine.setDelay(currentDelay);

    float dry = input;
    float wet = delayLine.tick(input, systemTime);

    return dry * (1.0f - mix) + wet * mix;
  }
}
