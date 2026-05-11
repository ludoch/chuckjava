package org.chuck.audio.filter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Quick diagnostic: run HPF ZDF SVF at morph=1.0, freq=20Hz with a 987Hz tone.
 * Checks whether the output frequency matches the input via autocorrelation.
 */
public class HPFToneTest {

  static final int SR = 44100;

  @Test
  void testHpfPreservesSineFrequency() {
    HPF hpf = new HPF(SR);
    hpf.freq(20.0);
    hpf.Q(0.707);

    // Generate a 987.77 Hz sine wave
    int len = SR * 2; // 2 seconds
    float[] input = new float[len];
    float[] output = new float[len];
    double freq = 987.77;
    for (int i = 0; i < len; i++) {
      input[i] = (float) Math.sin(2.0 * Math.PI * freq * i / SR);
    }

    // Process through HPF using compute() sample-by-sample
    for (int i = 0; i < len; i++) {
      output[i] = hpf.compute(input[i], -1);
    }

    // Autocorrelation frequency estimation (same algo as the test)
    int steadyStart = SR / 5;
    int steadyEnd = Math.min(len, steadyStart + SR / 2);
    int segLen = steadyEnd - steadyStart;
    float[] seg = new float[segLen];
    System.arraycopy(output, steadyStart, seg, 0, segLen);

    // Autocorrelation
    int minLag = SR / 2000; // ~22 for 987Hz at 44100
    int maxLag = SR / 400;  // ~110
    double bestCorr = 0;
    int bestLag = 0;
    for (int lag = minLag; lag <= maxLag; lag++) {
      double corr = 0;
      double s1 = 0, s2 = 0;
      for (int i = 0; i < segLen - lag; i++) {
        corr += seg[i] * seg[i + lag];
        s1 += seg[i] * seg[i];
        s2 += seg[i + lag] * seg[i + lag];
      }
      double norm = Math.sqrt(s1 * s2);
      if (norm > 0) corr /= norm;
      if (corr > bestCorr) { bestCorr = corr; bestLag = lag; }
    }
    double estFreq = bestLag > 0 ? (double) SR / bestLag : 0;
    double[] candidates = {estFreq, estFreq / 2, estFreq / 3, estFreq / 4, estFreq * 2};
    double bestCandidate = estFreq;
    double bestCandidateErr = Double.MAX_VALUE;
    for (double c : candidates) {
      if (c <= 0) continue;
      double err = Math.abs(c - freq) / freq;
      if (err < bestCandidateErr) { bestCandidateErr = err; bestCandidate = c; }
    }

    // Also compute average frequency from zero crossings
    int zeroCrossings = 0;
    for (int i = 1; i < segLen; i++) {
      if (seg[i - 1] <= 0 && seg[i] > 0) zeroCrossings++;
    }
    double zcFreq = (double) zeroCrossings * SR / segLen;

    System.out.println("HPF ZDF SVF frequency test:");
    System.out.println("  Input freq: " + freq + " Hz");
    System.out.println("  Autocorr raw: " + String.format("%.2f", estFreq) + " Hz (lag=" + bestLag + " corr=" + String.format("%.4f", bestCorr) + ")");
    System.out.println("  Best candidate: " + String.format("%.2f", bestCandidate) + " Hz err=" + String.format("%.3f", bestCandidateErr));
    System.out.println("  Zero-cross freq: " + String.format("%.2f", zcFreq) + " Hz");
    System.out.println("  Output peak: " + maxAbs(output));

    // Also check THD / waveform quality
    double dc = 0;
    for (int i = steadyStart; i < steadyEnd; i++) dc += output[i];
    dc /= segLen;
    System.out.println("  DC offset: " + String.format("%.6f", dc));

    assertTrue(bestCandidateErr < 0.15, "Frequency error too high: " + bestCandidateErr);
  }

  private static double maxAbs(float[] arr) {
    double m = 0;
    for (float v : arr) if (Math.abs(v) > m) m = Math.abs(v);
    return m;
  }
}
