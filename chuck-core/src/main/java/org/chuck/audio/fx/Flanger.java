package org.chuck.audio.fx;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.osc.SinOsc;

/** A flanger effect with feedback. */
public class Flanger extends ChuckUGen {
  private final DelayL delayLine;
  private final SinOsc lfo;
  private float modDepth = 0.5f; // 0-1 fraction of max delay
  private float baseDelaySamples;
  private float maxDelaySamples;
  private float feedback = 0.0f;
  private float mix = 0.5f;
  private float lastOutput = 0.0f;

  public Flanger(float sampleRate) {
    // Flanger: base delay ~1-5ms, modulated
    this.maxDelaySamples = 0.010f * sampleRate; // 10ms max
    this.baseDelaySamples = 0.001f * sampleRate; // 1ms base
    this.delayLine = new DelayL((int) (maxDelaySamples * 2));
    this.lfo = new SinOsc(sampleRate);
    this.lfo.setFreq(0.25);
  }

  public void setModFreq(double freq) {
    lfo.setFreq(freq);
  }

  public void setModDepth(float depth) {
    this.modDepth = depth;
  }

  public void setFeedback(float fb) {
    this.feedback = Math.max(-0.95f, Math.min(0.95f, fb));
  }

  public void setMix(float mix) {
    this.mix = mix;
  }

  @Override
  protected float compute(float input, long systemTime) {
    float lfoOut = lfo.tick(systemTime, systemTime);
    double currentDelay =
        baseDelaySamples + (maxDelaySamples - baseDelaySamples) * modDepth * (lfoOut * 0.5f + 0.5f);
    delayLine.setDelay(currentDelay);

    float wet = delayLine.tick(input + lastOutput * feedback, systemTime);
    lastOutput = wet;

    return input * (1.0f - mix) + wet * mix;
  }
}
