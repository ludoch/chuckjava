package org.chuck.audio.filter;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.ChuckDSL;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests HPF ZDF SVF when used with tick() and block-based processing,
 * simulating how the engine uses it in the audio chain.
 */
public class HPFBlockTest {

  static final int SR = 44100;
  static final int BLOCK_SIZE = 256;

  /**
   * A simple sine oscillator UGen for testing.
   */
  static class SineUGen extends ChuckUGen {
    final double freq;
    final float sr;
    double phase = 0;

    SineUGen(float sr, double freq) { this.sr = sr; this.freq = freq; }

    @Override
    public void tick(float[] buffer, int offset, int length, long systemTime) {
      for (int i = 0; i < length; i++) {
        buffer[offset + i] = (float) Math.sin(phase);
        phase += 2.0 * Math.PI * freq / sr;
        while (phase > Math.PI) phase -= 2.0 * Math.PI;
      }
      lastOut = buffer[offset + length - 1];
      lastTickTime = systemTime;
    }

    @Override
    protected float compute(float input, long systemTime) { return (float) Math.sin(phase++); }
  }

  @Test
  void testHpfTickBlockProcessing() {
    HPF hpf = new HPF(SR);
    hpf.freq(20.0);
    hpf.Q(0.707);

    SineUGen sine = new SineUGen(SR, 987.77);
    sine.chuck(hpf);

    // Process block by block like the engine
    int totalSamples = SR * 2; // 2 seconds
    int numBlocks = totalSamples / BLOCK_SIZE;
    float[] allOut = new float[totalSamples];

    for (int b = 0; b < numBlocks; b++) {
      int offset = b * BLOCK_SIZE;
      float[] buf = new float[BLOCK_SIZE];
      hpf.tick(buf, 0, BLOCK_SIZE, b); // systemTime = b to break caching
      System.arraycopy(buf, 0, allOut, offset, BLOCK_SIZE);
    }

    // Autocorrelation
    int steadyStart = SR / 5;
    int steadyEnd = Math.min(totalSamples, steadyStart + SR / 2);
    int segLen = steadyEnd - steadyStart;
    float[] seg = new float[segLen];
    System.arraycopy(allOut, steadyStart, seg, 0, segLen);

    double freq = 987.77;
    int minLag = SR / 2000;
    int maxLag = SR / 400;
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

    System.out.println("HPF ZDF SVF block tick test:");
    System.out.println("  Input freq: " + freq + " Hz");
    System.out.println("  Autocorr raw: " + String.format("%.2f", estFreq) + " Hz (lag=" + bestLag + " corr=" + String.format("%.4f", bestCorr) + ")");
    System.out.println("  Best candidate: " + String.format("%.2f", bestCandidate) + " Hz err=" + String.format("%.3f", bestCandidateErr));

    assertTrue(bestCandidateErr < 0.15, "Frequency error too high: " + bestCandidateErr);
  }
}
