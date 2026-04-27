package org.chuck.audio.filter;

import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;

/**
 * Two-pole resonance filter. Matches native ChucK (ugen_filter.cpp). H(z) = b0 / (1 + a1*z^-1 +
 * a2*z^-2)
 */
public class TwoPole extends ChuckUGen {
  private double b0 = 1.0;
  private double a1 = 0.0;
  private double a2 = 0.0;

  private double out1 = 0.0; // y[n-1]
  private double out2 = 0.0; // y[n-2]

  private double resFreq = 440.0;
  private double resRad = 0.0;
  private boolean resNorm = false;

  private final float sampleRate;

  public TwoPole() {
    this(44100.0f, true);
  }

  public TwoPole(float sampleRate) {
    this(sampleRate, true);
  }

  public TwoPole(float sampleRate, boolean autoRegister) {
    super(autoRegister);
    this.sampleRate = sampleRate;
  }

  /** Set resonance at given frequency (Hz) and pole radius [0,1). */
  public void setResonance(double frequency, double radius, boolean normalize) {
    resFreq = frequency;
    resRad = radius;
    resNorm = normalize;

    a2 = radius * radius;
    a1 = -2.0 * radius * Math.cos(2.0 * Math.PI * frequency / sampleRate);

    if (normalize) {
      // Native ChucK normalization (ugen_filter.cpp: b0 = 0.5 - 0.5 * a2)
      b0 = 0.5 - 0.5 * a2;
    } else {
      b0 = 1.0;
    }
  }

  public void setResonance(double frequency, double radius) {
    setResonance(frequency, radius, resNorm);
  }

  // ChucK-style accessors
  public double freq(double f) {
    resFreq = f;
    setResonance(resFreq, resRad, resNorm);
    return f;
  }

  public double freq() {
    return resFreq;
  }

  public double radius(double r) {
    resRad = r;
    setResonance(resFreq, resRad, resNorm);
    return r;
  }

  public double radius() {
    return resRad;
  }

  public int norm(int v) {
    setResonance(resFreq, resRad, v != 0);
    return v;
  }

  // Raw coefficient setters
  public void setB0(double v) {
    b0 = v;
  }

  public void setA1(double v) {
    a1 = v;
  }

  public void setA2(double v) {
    a2 = v;
  }

  @Override
  public void tick(float[] buffer, int offset, int length, long systemTime) {
    if (systemTime != -1
        && systemTime == blockStartTime
        && blockCache != null
        && blockLength >= length) {
      if (buffer != null) System.arraycopy(blockCache, 0, buffer, offset, length);
      return;
    }
    if (blockCache == null || blockCache.length < length) blockCache = new float[length];

    // 1. Sum inputs using SIMD
    float[] inputSum = new float[length];
    if (getNumSources() > 0) {
      for (ChuckUGen src : getSources()) {
        float[] temp = new float[length];
        src.tick(temp, 0, length, systemTime);

        int i = 0;
        int bound = SPECIES.loopBound(length);
        for (; i < bound; i += SPECIES.length()) {
          FloatVector v1 = FloatVector.fromArray(SPECIES, inputSum, i);
          FloatVector v2 = FloatVector.fromArray(SPECIES, temp, i);
          v1.add(v2).intoArray(inputSum, i);
        }
        for (; i < length; i++) inputSum[i] += temp[i];
      }
    } else {
      if (buffer != null) System.arraycopy(buffer, offset, inputSum, 0, length);
    }

    // 2. Apply filter
    double localB0 = b0;
    double localA1 = a1;
    double localA2 = a2;
    double localOut1 = out1;
    double localOut2 = out2;
    float localGain = gain;

    for (int i = 0; i < length; i++) {
      double y = localB0 * inputSum[i] - localA1 * localOut1 - localA2 * localOut2;
      localOut2 = localOut1;
      localOut1 = y;
      blockCache[i] = (float) (y * localGain);
      if (buffer != null) buffer[offset + i] = blockCache[i];
    }
    out1 = localOut1;
    out2 = localOut2;

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = (systemTime == -1) ? -1 : systemTime + length - 1;
    if (length > 0) lastOut = blockCache[length - 1];
  }

  @Override
  protected float compute(float input, long systemTime) {
    double y = b0 * input - a1 * out1 - a2 * out2;
    out2 = out1;
    out1 = y;
    return (float) y;
  }
}
