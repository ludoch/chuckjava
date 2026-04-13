package org.chuck.audio.chugins;

import org.chuck.audio.ChuckUGen;

/**
 * ExpDelay — echo with exponentially spaced taps, decaying in amplitude.
 *
 * <p>Scatters {@code reps} delay taps whose positions follow an exponential curve from 0 to {@code
 * delay} samples, and whose amplitudes decay exponentially. The dry/wet {@code mix} blends the wet
 * echo sum with the dry input.
 *
 * <p>Port of chugins/ExpDelay.
 */
public class ExpDelay extends ChuckUGen {
  private static final int DEFAULT_BUFLEN = 88200; // ~2 s at 44100
  private static final int DEFAULT_NUMPTS = 10;
  private static final double DEFAULT_CURVE = 2.0;

  private float[] delBuf;
  private int maxBufLen;
  private int bufLen;
  private int bufIndex = 0;

  private int numPts = DEFAULT_NUMPTS;
  private double durCurve = DEFAULT_CURVE;
  private double ampCurve = DEFAULT_CURVE;
  private float gainScale;
  private float mix = 1.0f;

  public ExpDelay(float sampleRate) {
    maxBufLen = DEFAULT_BUFLEN;
    bufLen = maxBufLen;
    delBuf = new float[maxBufLen];
    recomputeGainScale();
  }

  /** Exponential interpolation: maps inval in [inlo,inhi] → [outlo,outhi] with power=curve. */
  private double experp(
      double inval, double inlo, double inhi, double curve, double outlo, double outhi) {
    double lerp = (inval - inlo) / (inhi - inlo);
    double expval = Math.pow(lerp, curve);
    return expval * (outhi - outlo) + outlo;
  }

  private void recomputeGainScale() {
    double amptotal = 0.0;
    for (int i = 0; i < numPts; i++) {
      amptotal += experp(i, 0, numPts, ampCurve, 1, 0);
    }
    gainScale = amptotal > 0.0 ? (float) (1.0 / amptotal) : 1.0f;
  }

  @Override
  protected float compute(float input, long systemTime) {
    // scatter input across exponentially spaced taps
    for (int i = 0; i < numPts; i++) {
      float amp = (float) experp(i, 0, numPts, ampCurve, 1, 0);
      int bufpt = (int) experp(i, 0, numPts, durCurve, 0, bufLen);
      int adjustedBufpt = (bufpt + bufIndex) % bufLen;
      delBuf[adjustedBufpt] += input * amp * gainScale;
    }
    float out = delBuf[bufIndex];
    delBuf[bufIndex] = 0.0f;
    bufIndex = (bufIndex + 1) % bufLen;
    return out * mix + input * (1.0f - mix);
  }

  public float mix(float m) {
    mix = Math.max(0.0f, Math.min(1.0f, m));
    return mix;
  }

  public float mix() {
    return mix;
  }

  public int reps(int p) {
    numPts = Math.max(1, p);
    recomputeGainScale();
    return numPts;
  }

  public int reps() {
    return numPts;
  }

  public double durcurve(double p) {
    durCurve = Math.max(0.0001, p);
    return durCurve;
  }

  public double durcurve() {
    return durCurve;
  }

  public double ampcurve(double p) {
    ampCurve = Math.max(0.0001, p);
    recomputeGainScale();
    return ampCurve;
  }

  public double ampcurve() {
    return ampCurve;
  }

  /** Set delay length in samples. */
  public double delay(double samps) {
    bufLen = Math.max(1, Math.min(maxBufLen, (int) samps));
    return bufLen;
  }

  public double delay() {
    return bufLen;
  }

  /** Set maximum delay buffer size in samples. Reallocates if larger. */
  public double max(double samps) {
    int newMax = Math.max(1, (int) samps);
    if (newMax > maxBufLen) {
      float[] newBuf = new float[newMax];
      System.arraycopy(delBuf, 0, newBuf, 0, maxBufLen);
      delBuf = newBuf;
      maxBufLen = newMax;
    }
    return maxBufLen;
  }

  public double max() {
    return maxBufLen;
  }
}
