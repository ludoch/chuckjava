package org.chuck.audio.analysis;

import java.util.Collections;
import org.chuck.audio.ChuckUGen;
import org.chuck.audio.UAna;
import org.chuck.audio.util.Complex;

/**
 * Kurtosis — Spectral Kurtosis UAna.
 *
 * <p>Measures the "tailedness" or impulsiveness of the spectral distribution. High kurtosis →
 * impulsive/transient sounds. Low/negative kurtosis → sustained/tonal sounds.
 *
 * <p>Formula: Kurtosis = E[(X-μ)^4] / σ^4
 *
 * <p>Connect after FFT: adc => FFT fft =^ Kurtosis kurt; kurt.upchuck(); kurt.fval() // kurtosis
 * value
 */
public class Kurtosis extends UAna {

  private float result = 0.0f;

  @Override
  protected float compute(float input, long systemTime) {
    return input; // pass through audio
  }

  @Override
  protected void computeUAna() {
    float[] mags = null;

    // Get magnitude spectrum from upstream FFT/UAna
    for (ChuckUGen src : getSources()) {
      if (src instanceof UAna u) {
        float[] fvals = u.getLastBlob().getFvals();
        if (fvals != null && fvals.length > 0) {
          mags = fvals;
          break;
        }
      }
    }

    if (mags == null || mags.length < 2) {
      result = 0.0f;
      lastBlob.setCvals(Collections.singletonList(new Complex(0, 0)));
      return;
    }

    int n = mags.length;
    double sum = 0.0;
    for (float m : mags) sum += m;
    double mean = sum / n;

    double var = 0.0;
    double m4 = 0.0;
    for (float m : mags) {
      double diff = m - mean;
      double diff2 = diff * diff;
      var += diff2;
      m4 += diff2 * diff2;
    }
    var /= n;
    m4 /= n;

    result = (var > 1e-20) ? (float) (m4 / (var * var)) : 0.0f;

    lastBlob.setCvals(Collections.singletonList(new Complex(result, 0)));
  }

  /** Compute Kurtosis directly from a magnitude spectrum (bypasses UAna graph). */
  public void computeFromSpectrum(float[] mags) {
    if (mags == null || mags.length < 2) {
      result = 0.0f;
      return;
    }
    double sum = 0.0;
    for (float m : mags) sum += m;
    double mean = sum / mags.length;
    double var = 0.0, m4 = 0.0;
    for (float m : mags) {
      double diff = m - mean;
      double d2 = diff * diff;
      var += d2;
      m4 += d2 * d2;
    }
    var /= mags.length;
    m4 /= mags.length;
    result = (var > 1e-20) ? (float) (m4 / (var * var)) : 0.0f;
  }

  /** Returns the last computed Kurtosis value. */
  public float getResult() {
    return result;
  }

  @Override
  public float last() {
    return result;
  }
}
