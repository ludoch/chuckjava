package org.chuck.audio.analysis;

import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

/**
 * Windowing — static factory for FFT window functions.
 *
 * <p>Matches the ChucK Windowing API: {@code Windowing.hann(n)}, {@code Windowing.hamming(n)},
 * {@code Windowing.blackman(n)}, {@code Windowing.blackmanHarris(n)}, {@code
 * Windowing.rectangular(n)}, {@code Windowing.triangular(n)}.
 *
 * <p>Each method returns a {@code float[n]} ChuckArray of window coefficients that can be passed to
 * {@code fft.window}.
 */
public class Windowing extends ChuckObject {

  public Windowing() {
    super(ChuckType.OBJECT);
  }

  /** Hann (von Hann) window: w[n] = 0.5 * (1 - cos(2π·n/(N-1))) */
  public static ChuckArray hann(long N) {
    int n = (int) N;
    ChuckArray a = new ChuckArray(ChuckType.ARRAY, n);
    for (int i = 0; i < n; i++) a.setFloat(i, 0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (n - 1))));
    return a;
  }

  /** Hamming window: w[n] = 0.54 - 0.46·cos(2π·n/(N-1)) */
  public static ChuckArray hamming(long N) {
    int n = (int) N;
    ChuckArray a = new ChuckArray(ChuckType.ARRAY, n);
    for (int i = 0; i < n; i++) a.setFloat(i, 0.54 - 0.46 * Math.cos(2.0 * Math.PI * i / (n - 1)));
    return a;
  }

  /** Blackman window: w[n] = 0.42 - 0.5·cos(2π·n/(N-1)) + 0.08·cos(4π·n/(N-1)) */
  public static ChuckArray blackman(long N) {
    int n = (int) N;
    ChuckArray a = new ChuckArray(ChuckType.ARRAY, n);
    for (int i = 0; i < n; i++)
      a.setFloat(
          i,
          0.42
              - 0.5 * Math.cos(2.0 * Math.PI * i / (n - 1))
              + 0.08 * Math.cos(4.0 * Math.PI * i / (n - 1)));
    return a;
  }

  /** Blackman-Harris window (4-term). */
  public static ChuckArray blackmanHarris(long N) {
    int n = (int) N;
    ChuckArray a = new ChuckArray(ChuckType.ARRAY, n);
    double a0 = 0.35875, a1 = 0.48829, a2 = 0.14128, a3 = 0.01168;
    for (int i = 0; i < n; i++)
      a.setFloat(
          i,
          a0
              - a1 * Math.cos(2.0 * Math.PI * i / (n - 1))
              + a2 * Math.cos(4.0 * Math.PI * i / (n - 1))
              - a3 * Math.cos(6.0 * Math.PI * i / (n - 1)));
    return a;
  }

  /** Rectangular (no windowing) window: w[n] = 1.0 */
  public static ChuckArray rectangular(long N) {
    int n = (int) N;
    ChuckArray a = new ChuckArray(ChuckType.ARRAY, n);
    for (int i = 0; i < n; i++) a.setFloat(i, 1.0);
    return a;
  }

  /** Triangular (Bartlett) window: w[n] = 1 - |2n/(N-1) - 1| */
  public static ChuckArray triangular(long N) {
    int n = (int) N;
    ChuckArray a = new ChuckArray(ChuckType.ARRAY, n);
    for (int i = 0; i < n; i++) a.setFloat(i, 1.0 - Math.abs(2.0 * i / (n - 1) - 1.0));
    return a;
  }

  /** Alias: Hann window (common alternate name). */
  public static ChuckArray hanning(long N) {
    return hann(N);
  }
}
