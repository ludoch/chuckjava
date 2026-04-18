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

  /** Verify LPF attenuates high frequencies. Input: 5000Hz sine, Cutoff: 100Hz. */
  public static boolean verifyLPF() {
    float sampleRate = 44100.0f;
    org.chuck.audio.osc.SinOsc sin = new org.chuck.audio.osc.SinOsc(sampleRate);
    sin.freq(5000.0);
    org.chuck.audio.filter.Lpf lpf = new org.chuck.audio.filter.Lpf(sampleRate);
    lpf.setCutoff(100.0f);

    // Connect sin => lpf
    lpf.addSource(sin);

    float rms = calculateRMS(lpf, 44100, sampleRate);
    // 5000Hz should be heavily attenuated by a 100Hz 1-pole LPF
    return rms < 0.05f;
  }

  /** Verify HPF attenuates low frequencies. Input: 100Hz sine, Cutoff: 5000Hz. */
  public static boolean verifyHPF() {
    float sampleRate = 44100.0f;
    org.chuck.audio.osc.SinOsc sin = new org.chuck.audio.osc.SinOsc(sampleRate);
    sin.freq(100.0);
    org.chuck.audio.filter.HPF hpf = new org.chuck.audio.filter.HPF(sampleRate);
    hpf.freq(5000.0);

    hpf.addSource(sin);

    float rms = calculateRMS(hpf, 44100, sampleRate);
    // 100Hz should be heavily attenuated by a 5000Hz HPF
    return rms < 0.05f;
  }

  /** Verify BPF passes center frequency. Input: 1000Hz sine, Center: 1000Hz. */
  public static boolean verifyBPF() {
    float sampleRate = 44100.0f;
    org.chuck.audio.osc.SinOsc sin = new org.chuck.audio.osc.SinOsc(sampleRate);
    sin.freq(1000.0);
    org.chuck.audio.filter.BPF bpf = new org.chuck.audio.filter.BPF(sampleRate);
    bpf.freq(1000.0);
    bpf.Q(10.0); // Narrow band

    bpf.addSource(sin);

    float rms = calculateRMS(bpf, 44100, sampleRate);
    // 1000Hz should pass through relatively strong (near 0.707)
    return rms > 0.5f;
  }
}
