package org.chuck.audio.analysis;

import java.util.ArrayList;
import java.util.List;
import org.chuck.audio.UAna;
import org.chuck.audio.util.Complex;
import org.chuck.core.ChuckArray;

/**
 * Fast Fourier Transform Unit Analyzer.
 *
 * <p>Accumulates samples in a ring buffer. Call upchuck() (ChucK: FFT => UAnaBlob) to trigger
 * analysis. Returns size/2 complex bins; fvals() gives magnitudes.
 *
 * <p>Window functions: NONE (0), HANN (1), HAMMING (2), BLACKMAN (3).
 */
public class FFT extends UAna {
  public static final int WIN_NONE = 0;
  public static final int WIN_HANN = 1;
  public static final int WIN_HAMMING = 2;
  public static final int WIN_BLACKMAN = 3;

  private int size;
  private int windowType = WIN_HANN;
  private float[] ring; // ring buffer of raw samples
  private int writePos = 0; // next write position
  private double[] win; // cached window coefficients
  private volatile float[] latestMags; // for UI thread
  private int samplesSinceLastFFT = 0;

  public FFT() {
    this(1024);
  }

  public FFT(int size) {
    setSize(size);
  }

  public void setSize(int newSize) {
    // Round down to nearest power of two
    int n = 1;
    while (n < newSize) n <<= 1;
    this.size = n;
    this.ring = new float[n];
    this.writePos = 0;
    buildWindow();
  }

  public int getSize() {
    return size;
  }

  /** ChucK API: {@code fft.size()} getter. */
  public long size() {
    return size;
  }

  /** ChucK API: {@code fft.size(n)} method-call setter. */
  public long size(long n) {
    setSize((int) n);
    return n;
  }

  /** ChucK API: {@code n => fft.size} property-setter (SetMemberIntByName uses double). */
  public double size(double n) {
    setSize((int) n);
    return n;
  }

  /** ChucK API: {@code fft.cval(n)} — returns complex bin n as a {@code complex} ChuckArray. */
  public ChuckArray cval(long n) {
    List<Complex> cv = lastBlob.getCvals();
    int idx = (int) n;
    ChuckArray res = new ChuckArray("complex", new double[] {0.0, 0.0});
    if (idx >= 0 && idx < cv.size()) {
      Complex c = cv.get(idx);
      res.setFloat(0, c.re());
      res.setFloat(1, c.im());
    }
    return res;
  }

  /**
   * ChucK API: {@code fft.spectrum(s)} — fills a pre-allocated {@code complex[]} array with the
   * current spectrum. Each element {@code s[i]} must already be a {@code complex} ChuckArray.
   */
  public ChuckArray spectrum(ChuckArray arr) {
    if (arr == null) return arr;
    List<Complex> cv = lastBlob.getCvals();
    int n = Math.min(arr.size(), cv.size());
    for (int i = 0; i < n; i++) {
      Complex c = cv.get(i);
      Object elem = arr.getObject(i);
      if (elem instanceof ChuckArray ca) {
        ca.setFloat(0, c.re());
        ca.setFloat(1, c.im());
      } else {
        ChuckArray ca2 = new ChuckArray("complex", new double[] {c.re(), c.im()});
        arr.setObject(i, ca2);
      }
    }
    return arr;
  }

  /** ChucK API: {@code fft.fval(n)} — returns magnitude of bin n. */
  public double fval(long n) {
    float[] fv = lastBlob.getFvals();
    int idx = (int) n;
    return (idx >= 0 && idx < fv.length) ? fv[idx] : 0.0;
  }

  public void setWindow(int type) {
    this.windowType = type;
    buildWindow();
  }

  public int getWindow() {
    return windowType;
  }

  /**
   * Set a custom window from a {@code ChuckArray} of coefficients (e.g. from {@code
   * Windowing.hann(n)}). The array length must match the FFT size; extra values are ignored and
   * short arrays are zero-padded.
   */
  public ChuckArray window(ChuckArray coeffs) {
    if (coeffs == null) return coeffs;
    win = new double[size];
    int len = Math.min(size, coeffs.size());
    for (int i = 0; i < len; i++) win[i] = coeffs.getFloat(i);
    // zero-pad remainder (already 0 from array init)
    return coeffs;
  }

  private void buildWindow() {
    win = new double[size];
    for (int i = 0; i < size; i++) {
      win[i] =
          switch (windowType) {
            case WIN_HANN -> 0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (size - 1)));
            case WIN_HAMMING -> 0.54 - 0.46 * Math.cos(2.0 * Math.PI * i / (size - 1));
            case WIN_BLACKMAN ->
                0.42
                    - 0.5 * Math.cos(2.0 * Math.PI * i / (size - 1))
                    + 0.08 * Math.cos(4.0 * Math.PI * i / (size - 1));
            default -> 1.0;
          };
    }
  }

  /** Pass input through; accumulate into ring buffer. */
  @Override
  protected float compute(float input, long systemTime) {
    ring[writePos] = input;
    writePos = (writePos + 1) % size;

    // Automatic upchuck for visualization (50% overlap)
    if (++samplesSinceLastFFT >= size / 2) {
      samplesSinceLastFFT = 0;
      computeUAna();
    }

    return input;
  }

  /**
   * Run in-place Cooley-Tukey FFT on the current ring buffer contents. Writes size/2 complex bins
   * into lastBlob.
   */
  @Override
  protected void computeUAna() {
    // Copy ring buffer into work arrays (oldest sample first), apply window
    double[] re = new double[size];
    double[] im = new double[size];
    for (int i = 0; i < size; i++) {
      int idx = (writePos + i) % size;
      re[i] = ring[idx] * win[i];
      // im[i] already 0
    }

    // In-place Cooley-Tukey DIT FFT (radix-2)
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

    // FFT butterfly stages
    for (int len = 2; len <= size; len <<= 1) {
      double ang = -2.0 * Math.PI / len;
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

    // Store first size/2 bins (positive frequencies)
    int bins = size / 2;
    List<Complex> spectrum = new ArrayList<>(bins);
    float[] magnitudes = new float[bins];
    for (int i = 0; i < bins; i++) {
      Complex c = new Complex((float) re[i], (float) im[i]);
      spectrum.add(c);
      magnitudes[i] = c.magnitude();
    }
    lastBlob.setCvals(spectrum);
    this.latestMags = magnitudes;
  }

  public float[] getLatestMags() {
    return latestMags;
  }
}
