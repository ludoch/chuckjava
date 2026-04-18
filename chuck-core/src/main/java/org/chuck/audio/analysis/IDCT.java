package org.chuck.audio.analysis;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.UAna;

/**
 * IDCT — Inverse Discrete Cosine Transform (Type III, inverse of DCT-II) UAna. Chains after DCT:
 * pulls fvals from upstream, reconstructs time-domain samples. x[n] = (1/N) * (X[0]/2 +
 * sum(k=1..N-1) X[k] * cos(pi * k * (2n+1) / (2N)))
 *
 * <p>Usage in ChucK: adc => DCT dct => IDCT idct => blackhole; while (true) { 512::samp => now;
 * idct.upchuck() @=> UAnaBlob blob; }
 */
public class IDCT extends UAna {
  @Override
  protected float compute(float input, long systemTime) {
    return input;
  }

  @Override
  protected void computeUAna() {
    // Pull coefficients from upstream DCT (or any UAna with fvals)
    float[] X = null;
    for (ChuckUGen src : getSources()) {
      if (src instanceof UAna u) {
        X = u.lastBlob.getFvals();
        break;
      }
    }
    if (X == null || X.length == 0) return;

    int N = X.length;
    float[] x = new float[N];
    double factor = Math.PI / (2.0 * N);
    double invN = 1.0 / N;
    for (int n = 0; n < N; n++) {
      double sum = X[0] * 0.5;
      for (int k = 1; k < N; k++) {
        sum += X[k] * Math.cos(factor * k * (2 * n + 1));
      }
      x[n] = (float) (sum * invN);
    }
    lastBlob.setFvals(x);
  }
}
