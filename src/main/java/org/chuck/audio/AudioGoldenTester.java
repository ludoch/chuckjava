package org.chuck.audio;

import org.chuck.audio.osc.SinOsc;

/** Utility to verify audio output properties of UGens. */
public class AudioGoldenTester {

  /** Runs a UGen for a fixed number of samples and returns the RMS power. */
  public static float calculateRMS(ChuckUGen ugen, int numSamples, float sampleRate) {
    float sumSq = 0;
    for (int i = 0; i < numSamples; i++) {
      float out = ugen.tick(i);
      sumSq += out * out;
    }
    return (float) Math.sqrt(sumSq / numSamples);
  }

  /** Simple verification for a Sine wave at gain 1.0. RMS of a sine wave is 1/sqrt(2) ≈ 0.707 */
  public static boolean verifySinOsc() {
    SinOsc sin = new SinOsc(44100.0f);
    sin.freq(440.0);
    sin.gain(1.0f);
    float rms = calculateRMS(sin, 44100, 44100.0f);
    return Math.abs(rms - 0.7071f) < 0.01f;
  }
}
