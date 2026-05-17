package org.chuck.audio.fx;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.osc.SinOsc;

/**
 * CHORUS_STEREO ModFX: stereo chorus with separate LFOs per channel and wider modulation spread.
 * Extends the basic Chorus with independent left/right modulation for a spacious stereo image.
 */
public class ChorusStereoModFx extends ChuckUGen {
  private final DelayL delayL, delayR;
  private final SinOsc lfoL, lfoR;
  private float modDepth = 0.3f;
  private float baseDelaySamples;
  private float maxDelaySamples;
  private float feedback = 0.0f;
  private float mix = 0.5f;
  private float lastOutL = 0.0f, lastOutR = 0.0f;

  public ChorusStereoModFx(float sampleRate) {
    this.baseDelaySamples = 0.025f * sampleRate; // 25ms base
    this.maxDelaySamples = 0.060f * sampleRate; // 60ms max
    int delayLen = (int) (maxDelaySamples * 2);
    this.delayL = new DelayL(delayLen);
    this.delayR = new DelayL(delayLen);
    this.lfoL = new SinOsc(sampleRate);
    this.lfoR = new SinOsc(sampleRate);
    this.lfoL.setFreq(0.3);
    // Right channel LFO 120 degrees out of phase for stereo spread
    this.lfoR.setFreq(0.3);
  }

  public void setModFreq(double freq) {
    lfoL.setFreq(freq);
    lfoR.setFreq(freq);
  }

  public void setModDepth(float depth) {
    this.modDepth = depth;
  }

  public void setFeedback(float fb) {
    this.feedback = Math.max(-0.9f, Math.min(0.9f, fb));
  }

  public void setMix(float mix) {
    this.mix = mix;
  }

  @Override
  protected float compute(float input, long systemTime) {
    // Right channel 120 degrees out of phase
    double phaseOffset = 1.0 / 3.0; // 120 degrees in phase units [0,1)
    float lfoLOut = lfoL.tick(systemTime, systemTime);
    float lfoROut = (float) Math.sin((lfoL.phase() + phaseOffset) * 2.0 * Math.PI);

    // Wider modulation range for stereo effect
    double delayLVal = baseDelaySamples * (1.0 + modDepth * lfoLOut);
    double delayRVal = baseDelaySamples * (1.0 + modDepth * lfoROut * 1.3);

    delayL.setDelay(delayLVal);
    delayR.setDelay(delayRVal);

    float wetL = delayL.tick(input + lastOutL * feedback * 0.707f, systemTime);
    float wetR = delayR.tick(input + lastOutR * feedback * 0.707f, systemTime);
    lastOutL = wetL;
    lastOutR = wetR;

    return input * (1.0f - mix) + (wetL + wetR) * 0.5f * mix;
  }
}
