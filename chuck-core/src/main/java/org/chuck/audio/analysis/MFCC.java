package org.chuck.audio.analysis;

import java.util.ArrayList;
import java.util.List;
import org.chuck.audio.UAna;
import org.chuck.audio.util.Complex;

/**
 * MFCC — Mel-Frequency Cepstral Coefficients UAna.
 *
 * <p>Computes MFCCs from the audio signal in frames: 1. Accumulate samples into a frame buffer. 2.
 * Compute FFT magnitude spectrum. 3. Apply mel-scale triangular filterbank. 4. Take log of
 * filterbank energies. 5. Apply DCT-II to get cepstral coefficients.
 *
 * <p>Default: 13 coefficients, 26 mel filters, 512-sample frames, 44100 Hz.
 */
public class MFCC extends UAna {
  private int numCoeffs;
  private int numFilters;
  private int fftSize;
  private final int sampleRate;
  private float[] coefficients;

  // Mel filterbank: filterbank[m][k] = weight for filter m at bin k
  private float[][] filterbank;

  // Internal frame buffer
  private float[] sampleBuffer;
  private int bufIdx = 0;

  public MFCC() {
    this(13, 26, 512, 44100);
  }

  public MFCC(int numCoeffs, int numFilters, int fftSize, int sampleRate) {
    this.numCoeffs = numCoeffs;
    this.numFilters = numFilters;
    this.fftSize = fftSize;
    this.sampleRate = sampleRate;
    this.coefficients = new float[numCoeffs];
    this.sampleBuffer = new float[fftSize];
    buildFilterbank();
  }

  private void buildFilterbank() {
    float melMin = hzToMel(0.0f);
    float melMax = hzToMel(sampleRate / 2.0f);

    // numFilters + 2 evenly-spaced mel points
    float[] melPoints = new float[numFilters + 2];
    for (int i = 0; i < melPoints.length; i++) {
      melPoints[i] = melMin + i * (melMax - melMin) / (numFilters + 1);
    }

    // Convert mel points to FFT bin indices
    int specSize = fftSize / 2 + 1;
    int[] binPoints = new int[melPoints.length];
    for (int i = 0; i < melPoints.length; i++) {
      float hz = melToHz(melPoints[i]);
      binPoints[i] = Math.min((int) Math.floor(specSize * hz / (sampleRate / 2.0f)), specSize - 1);
    }

    // Build triangular filters
    filterbank = new float[numFilters][specSize];
    for (int m = 0; m < numFilters; m++) {
      int left = binPoints[m];
      int center = binPoints[m + 1];
      int right = binPoints[m + 2];
      for (int k = left; k < center; k++) {
        if (k >= 0 && k < specSize && center > left)
          filterbank[m][k] = (float) (k - left) / (center - left);
      }
      for (int k = center; k <= right; k++) {
        if (k >= 0 && k < specSize && right > center)
          filterbank[m][k] = (float) (right - k) / (right - center);
      }
    }
  }

  private static float hzToMel(float hz) {
    return 2595.0f * (float) Math.log10(1.0f + hz / 700.0f);
  }

  private static float melToHz(float mel) {
    return 700.0f * ((float) Math.pow(10.0, mel / 2595.0) - 1.0f);
  }

  /** Compute MFCCs from an external magnitude spectrum (e.g., from FFT). */
  public void computeFromSpectrum(float[] magSpectrum) {
    int specSize = magSpectrum.length;
    float[] filterEnergies = new float[numFilters];
    for (int m = 0; m < numFilters; m++) {
      for (int k = 0; k < Math.min(specSize, filterbank[m].length); k++) {
        filterEnergies[m] += magSpectrum[k] * filterbank[m][k];
      }
      // Log compression with a small floor to avoid -Inf
      filterEnergies[m] = (float) Math.log(filterEnergies[m] + 1e-10f);
    }
    // DCT-II to produce cepstral coefficients
    for (int n = 0; n < numCoeffs; n++) {
      float sum = 0.0f;
      for (int m = 0; m < numFilters; m++) {
        sum += filterEnergies[m] * (float) Math.cos(Math.PI * n * (m + 0.5) / numFilters);
      }
      coefficients[n] = sum;
    }
    // Publish to blob for upchuck()
    List<Complex> cvals = new ArrayList<>(numCoeffs);
    for (float c : coefficients) cvals.add(new Complex(c, 0));
    lastBlob.setCvals(cvals);
  }

  @Override
  protected float compute(float input, long systemTime) {
    sampleBuffer[bufIdx++] = input;
    if (bufIdx >= fftSize) {
      float[] mag = computeFFTMagnitude(sampleBuffer);
      computeFromSpectrum(mag);
      bufIdx = 0;
    }
    return input; // pass audio through
  }

  @Override
  protected void computeUAna() {
    // Re-compute on the current buffer contents on upchuck()
    float[] mag = computeFFTMagnitude(sampleBuffer);
    computeFromSpectrum(mag);
  }

  /**
   * Compute magnitude spectrum via DFT. Intentionally simple for correctness; adequate for
   * offline/slow analysis.
   */
  private float[] computeFFTMagnitude(float[] samples) {
    int n = samples.length;
    int specSize = n / 2 + 1;
    float[] mag = new float[specSize];
    for (int k = 0; k < specSize; k++) {
      double re = 0.0, im = 0.0;
      double twoPiKoverN = 2.0 * Math.PI * k / n;
      for (int t = 0; t < n; t++) {
        re += samples[t] * Math.cos(twoPiKoverN * t);
        im -= samples[t] * Math.sin(twoPiKoverN * t);
      }
      mag[k] = (float) Math.sqrt(re * re + im * im) / n;
    }
    return mag;
  }

  /** Get the most-recently computed MFCC coefficients array. */
  public float[] getCoefficients() {
    return coefficients;
  }

  /** Get a specific coefficient by index. */
  public double getCoeff(int idx) {
    if (idx >= 0 && idx < coefficients.length) return coefficients[idx];
    return 0.0;
  }

  public int getNumCoeffs() {
    return numCoeffs;
  }

  public int getNumFilters() {
    return numFilters;
  }

  public int getFftSize() {
    return fftSize;
  }

  public void setNumCoeffs(int n) {
    this.numCoeffs = n;
    this.coefficients = new float[n];
  }

  public void setNumFilters(int n) {
    this.numFilters = n;
    buildFilterbank();
  }

  public void setFftSize(int n) {
    this.fftSize = n;
    this.sampleBuffer = new float[n];
    this.bufIdx = 0;
    buildFilterbank();
  }
}
