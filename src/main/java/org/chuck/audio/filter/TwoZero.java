package org.chuck.audio.filter;

import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;

/**
 * Two-zero (FIR) notch filter. H(z) = b0 + b1*z^-1 + b2*z^-2
 *
 * <p>Use setNotch(freq, radius) for a normalized notch, or set raw b0/b1/b2.
 */
public class TwoZero extends ChuckUGen {
  private double b0 = 1.0;
  private double b1 = 0.0;
  private double b2 = 0.0;

  private double in1 = 0.0; // x[n-1]
  private double in2 = 0.0; // x[n-2]

  private double notchFreq = 440.0;
  private double notchRad = 0.0;

  private final float sampleRate;

  public TwoZero(float sampleRate) {
    this.sampleRate = sampleRate;
  }

  /**
   * Place two zeros at the given frequency (Hz) and radius, normalized so gain = 1 everywhere
   * except the notch.
   */
  public void setNotch(double frequency, double radius) {
    notchFreq = frequency;
    notchRad = radius;
    b2 = radius * radius;
    b1 = -2.0 * radius * Math.cos(2.0 * Math.PI * frequency / sampleRate);
    // Normalize for unity gain
    double norm;
    if (b1 > 0.0) norm = 1.0 / (1.0 + b1 + b2); // peak at z = -1
    else norm = 1.0 / (1.0 - b1 + b2); // peak at z = +1
    b0 = norm;
    b1 *= norm;
    b2 *= norm;
  }

  // ChucK-style accessors
  public double freq(double f) {
    notchFreq = f;
    setNotch(notchFreq, notchRad);
    return f;
  }

  public double freq() {
    return notchFreq;
  }

  public double radius(double r) {
    notchRad = r;
    setNotch(notchFreq, notchRad);
    return r;
  }

  public double radius() {
    return notchRad;
  }

  // Raw coefficient setters
  public void setB2(double v) {
    b2 = v;
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
      for (ChuckUGen src : sources) {
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
    double localB1 = b1;
    double localB2 = b2;
    double localIn1 = in1;
    double localIn2 = in2;
    float localGain = gain;

    for (int i = 0; i < length; i++) {
      double currentInput = inputSum[i];
      double y = localB0 * currentInput + localB1 * localIn1 + localB2 * localIn2;
      localIn2 = localIn1;
      localIn1 = currentInput;
      blockCache[i] = (float) (y * localGain);
      if (buffer != null) buffer[offset + i] = blockCache[i];
    }
    in1 = localIn1;
    in2 = localIn2;

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = (systemTime == -1) ? -1 : systemTime + length - 1;
    if (length > 0) lastOut = blockCache[length - 1];
  }

  @Override
  protected float compute(float input, long systemTime) {
    double y = b0 * input + b1 * in1 + b2 * in2;
    in2 = in1;
    in1 = input;
    return (float) y;
  }
}
