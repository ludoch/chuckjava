package org.chuck.audio.fx;

import org.chuck.audio.ChuckUGen;

/**
 * DIMENSION ModFX: Boss-style stereo chorus with 3 voices and offset delays.
 * Each voice uses a triangle LFO at slightly different phase to create a wide stereo image.
 */
public class DimensionModFx extends ChuckUGen {
  private final DelayL[] delays;
  private final double[] phases;
  private double lfoRate = 0.2;
  private double modDepth = 0.4;
  private double mix = 0.5;
  private double sampleRate;
  private int maxDelaySamples;
  private static final int VOICES = 3;
  // Voice offsets (seconds)
  private static final double[] VOICE_DELAYS = {0.008, 0.014, 0.020};

  public DimensionModFx(float sampleRate) {
    this.sampleRate = sampleRate;
    this.maxDelaySamples = (int) (0.050f * sampleRate) + 4;
    this.delays = new DelayL[VOICES];
    this.phases = new double[]{0.0, 2.0 * Math.PI / 3.0, 4.0 * Math.PI / 3.0};
    for (int i = 0; i < VOICES; i++) {
      delays[i] = new DelayL(maxDelaySamples);
      delays[i].setDelay(VOICE_DELAYS[i] * sampleRate);
    }
  }

  public void setModFreq(double freq) { this.lfoRate = freq; }

  public void setModDepth(double depth) { this.modDepth = Math.max(0.0, Math.min(1.0, depth)); }

  public void setMix(double mix) { this.mix = mix; }

  @Override
  protected float compute(float input, long systemTime) {
    // Triangle LFO
    double phase = (lfoRate / sampleRate);
    float wetSum = 0.0f;

    for (int i = 0; i < VOICES; i++) {
      phases[i] = (phases[i] + phase) % 1.0;
      double lfo;
      if (phases[i] < 0.5) {
        lfo = 4.0 * phases[i] - 1.0; // rise: -1 to +1
      } else {
        lfo = 3.0 - 4.0 * phases[i]; // fall: +1 to -1
      }
      // Modulate delay around base by depth
      double baseDelay = VOICE_DELAYS[i] * sampleRate;
      double delayMod = lfo * modDepth * 0.005 * sampleRate;
      delays[i].setDelay(Math.max(2.0, baseDelay + delayMod));
      wetSum += delays[i].tick(input, systemTime);
    }

    float wet = wetSum / VOICES;
    return input * (1.0f - (float) mix) + wet * (float) mix;
  }
}
