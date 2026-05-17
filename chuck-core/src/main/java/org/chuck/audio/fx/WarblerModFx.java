package org.chuck.audio.fx;

import org.chuck.audio.ChuckUGen;

/**
 * WARBLER ModFX: modulated delay-tap with dual LFOs and resonance-compensated feedback. Emulates
 * the Deluge firmware's warble effect with a random-walk LFO driving delay modulation.
 */
public class WarblerModFx extends ChuckUGen {
  private final DelayL delayLine;
  private double lfo1Phase = 0.0, lfo2Phase = 0.0;
  private double lfoRate = 0.5;
  private double modDepth = 0.3;
  private double feedback = 0.0;
  private double mix = 0.5;
  private double sampleRate;
  private double warbler1 = 0.0, warbler2 = 0.0;
  private double lastWet = 0.0;
  private int maxDelaySamples;

  public WarblerModFx(float sampleRate) {
    this.sampleRate = sampleRate;
    // 50ms max delay for warble
    this.maxDelaySamples = (int) (0.050f * sampleRate) + 4;
    this.delayLine = new DelayL(maxDelaySamples);
    // Base delay ~15ms
    delayLine.setDelay(0.015 * sampleRate);
  }

  public void setModFreq(double freq) {
    this.lfoRate = freq;
  }

  public void setModDepth(double depth) {
    this.modDepth = Math.max(0.0, Math.min(1.0, depth));
  }

  public void setFeedback(double fb) {
    this.feedback = Math.max(-0.9, Math.min(0.9, fb));
  }

  public void setMix(double mix) {
    this.mix = mix;
  }

  @Override
  protected float compute(float input, long systemTime) {
    // Dual warbler LFOs
    lfo1Phase = (lfo1Phase + lfoRate / sampleRate) % 1.0;
    lfo2Phase = (lfo2Phase + lfoRate * 0.7 / sampleRate) % 1.0;

    // Warble: random-walk with second-order smoothing
    warbler1 += (Math.random() - 0.5) * 0.1;
    warbler1 *= 0.99;
    warbler2 += (warbler1 - warbler2) * 0.2;
    warbler2 = Math.max(-1.0, Math.min(1.0, warbler2));

    // Combine sin LFO with warble, modulated by depth
    double combinedLfo = 0.7 * Math.sin(lfo1Phase * 2.0 * Math.PI) + 0.3 * warbler2;
    double delayMod = combinedLfo * modDepth * 0.02 * sampleRate;
    double baseDelay = 0.015 * sampleRate;
    double currentDelay = Math.max(2.0, Math.min(maxDelaySamples - 2, baseDelay + delayMod));
    delayLine.setDelay(currentDelay);

    float wet = delayLine.tick(input + (float) (lastWet * feedback * 0.707), systemTime);
    lastWet = wet;

    return input * (1.0f - (float) mix) + wet * (float) mix;
  }
}
