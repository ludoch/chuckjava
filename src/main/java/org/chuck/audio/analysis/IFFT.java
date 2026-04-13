package org.chuck.audio.analysis;

import java.util.ArrayList;
import java.util.List;
import org.chuck.audio.ChuckUGen;
import org.chuck.audio.UAna;
import org.chuck.audio.UAnaBlob;
import org.chuck.audio.util.Complex;

/**
 * Inverse Fast Fourier Transform (IFFT) Unit Analyzer.
 *
 * <p>Takes spectral data (Complex bins) and converts it back to time-domain samples. Usually
 * connected after an FFT or other spectral processor: FFT f => IFFT i => dac;
 */
public class IFFT extends UAna {
  private int size;
  private float[] buffer;
  private int readPos = 0;

  public IFFT() {
    this(1024);
  }

  public IFFT(int size) {
    setSize(size);
  }

  public void setSize(int newSize) {
    int n = 1;
    while (n < newSize) n <<= 1;
    this.size = n;
    this.buffer = new float[n];
    this.readPos = 0;
  }

  @Override
  protected float compute(float input, long systemTime) {
    // Output the next sample from the resynthesized buffer
    float out = buffer[readPos];
    readPos = (readPos + 1) % size;
    return out;
  }

  @Override
  protected void computeUAna() {
    // Find an upstream UAna to get spectral data from
    UAnaBlob inputBlob = null;
    for (ChuckUGen src : getSources()) {
      if (src instanceof UAna u) {
        inputBlob = u.getLastBlob();
        break;
      }
    }

    if (inputBlob == null) return;

    List<Complex> cvals = inputBlob.getCvals();
    if (cvals.isEmpty()) return;

    // Prepare full spectrum (reconstruct negative frequencies for real signal)
    double[] re = new double[size];
    double[] im = new double[size];

    int bins = Math.min(cvals.size(), size / 2);
    for (int i = 0; i < bins; i++) {
      Complex c = cvals.get(i);
      re[i] = c.re();
      im[i] = c.im();
      // Conjugate symmetry for real output
      if (i > 0 && i < size - i) {
        re[size - i] = c.re();
        im[size - i] = -c.im();
      }
    }

    // In-place Inverse FFT
    // 1. Swap real and imaginary parts (or use conjugate)
    // 2. Perform forward FFT
    // 3. Swap again and scale by 1/N

    // Bit-reversal permutation
    int bits = Integer.numberOfTrailingZeros(size);
    for (int i = 0; i < size; i++) {
      int j = Integer.reverse(i) >>> (32 - bits);
      if (j > i) {
        double tmp = re[i];
        re[i] = re[j];
        re[j] = tmp;
        tmp = im[i];
        im[i] = im[j];
        im[j] = tmp;
      }
    }

    // FFT stages (inverse uses positive angle)
    for (int len = 2; len <= size; len <<= 1) {
      double ang = 2.0 * Math.PI / len; // Positive for Inverse
      double wRe = Math.cos(ang);
      double wIm = Math.sin(ang);
      for (int i = 0; i < size; i += len) {
        double curRe = 1.0, curIm = 0.0;
        for (int j = 0; j < len / 2; j++) {
          int u = i + j, v = i + j + len / 2;
          double uRe = re[u], uIm = im[u];
          double vRe = re[v] * curRe - im[v] * curIm;
          double vIm = re[v] * curIm + im[v] * curRe;
          re[u] = uRe + vRe;
          im[u] = uIm + vIm;
          re[v] = uRe - vRe;
          im[v] = uIm - vIm;
          double nextRe = curRe * wRe - curIm * wIm;
          curIm = curRe * wIm + curIm * wRe;
          curRe = nextRe;
        }
      }
    }

    // Scale by 1/N and copy to output buffer
    for (int i = 0; i < size; i++) {
      buffer[i] = (float) (re[i] / size);
    }

    // Reset read position to start of new frame
    readPos = 0;

    // IFFT doesn't produce its own blob for downstream UAnas in standard ChucK,
    // but we can store the time-domain samples if needed.
    // ChucK IFFT => UAnaBlob gives time domain samples.
    List<Complex> timeDomain = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      timeDomain.add(new Complex(buffer[i], 0));
    }
    lastBlob.setCvals(timeDomain);
  }
}
