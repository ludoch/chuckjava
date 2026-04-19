package org.chuck.audio.filter;

import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/**
 * Two-band shelving equalizer (Bass and Treble) suitable for the Deluge EQ section. Cascades a
 * low-shelf and high-shelf biquad filter.
 */
@doc("Two-band shelving equalizer (Bass and Treble).")
public class ShelfEQ extends ChuckUGen {
  private float sampleRate;

  private float bassFreq = 80.0f;
  private float bassGainDB = 0.0f;
  private float trebleFreq = 8000.0f;
  private float trebleGainDB = 0.0f;

  // Fixed Q for standard shelving
  private static final double Q = Math.sqrt(2.0) / 2.0;

  // low shelf coeffs
  private float lb0, lb1, lb2, la1, la2;
  // high shelf coeffs
  private float hb0, hb1, hb2, ha1, ha2;

  // state
  private float lx1 = 0, lx2 = 0, ly1 = 0, ly2 = 0;
  private float hx1 = 0, hx2 = 0, hy1 = 0, hy2 = 0;

  public ShelfEQ() {
    this(org.chuck.core.ChuckVM.CURRENT_VM.get().getSampleRate());
  }

  public ShelfEQ(float sampleRate) {
    this.sampleRate = sampleRate;
    calcCoeffs();
  }

  public double bassFreq(double f) {
    this.bassFreq = (float) Math.max(10.0, Math.min(sampleRate / 2.0, f));
    calcCoeffs();
    return this.bassFreq;
  }

  public double bassFreq() {
    return bassFreq;
  }

  public double bassGain(double db) {
    this.bassGainDB = (float) db;
    calcCoeffs();
    return this.bassGainDB;
  }

  public double bassGain() {
    return bassGainDB;
  }

  public double trebleFreq(double f) {
    this.trebleFreq = (float) Math.max(10.0, Math.min(sampleRate / 2.0, f));
    calcCoeffs();
    return this.trebleFreq;
  }

  public double trebleFreq() {
    return trebleFreq;
  }

  public double trebleGain(double db) {
    this.trebleGainDB = (float) db;
    calcCoeffs();
    return this.trebleGainDB;
  }

  public double trebleGain() {
    return trebleGainDB;
  }

  private void calcCoeffs() {
    // 1. Low Shelf calculations (Cookbook formulas)
    double A = Math.pow(10.0, bassGainDB / 40.0);
    double w0 = 2.0 * Math.PI * bassFreq / sampleRate;
    double alpha = Math.sin(w0) / (2.0 * Q);
    double cosw0 = Math.cos(w0);

    double a0 = (A + 1.0) + (A - 1.0) * cosw0 + 2.0 * Math.sqrt(A) * alpha;
    double b0 = A * ((A + 1.0) - (A - 1.0) * cosw0 + 2.0 * Math.sqrt(A) * alpha);
    double b1 = 2.0 * A * ((A - 1.0) - (A + 1.0) * cosw0);
    double b2 = A * ((A + 1.0) - (A - 1.0) * cosw0 - 2.0 * Math.sqrt(A) * alpha);
    double a1 = -2.0 * ((A - 1.0) + (A + 1.0) * cosw0);
    double a2 = (A + 1.0) + (A - 1.0) * cosw0 - 2.0 * Math.sqrt(A) * alpha;

    lb0 = (float) (b0 / a0);
    lb1 = (float) (b1 / a0);
    lb2 = (float) (b2 / a0);
    la1 = (float) (a1 / a0);
    la2 = (float) (a2 / a0);

    // 2. High Shelf calculations
    A = Math.pow(10.0, trebleGainDB / 40.0);
    w0 = 2.0 * Math.PI * trebleFreq / sampleRate;
    alpha = Math.sin(w0) / (2.0 * Q);
    cosw0 = Math.cos(w0);

    a0 = (A + 1.0) - (A - 1.0) * cosw0 + 2.0 * Math.sqrt(A) * alpha;
    b0 = A * ((A + 1.0) + (A - 1.0) * cosw0 + 2.0 * Math.sqrt(A) * alpha);
    b1 = -2.0 * A * ((A - 1.0) + (A + 1.0) * cosw0);
    b2 = A * ((A + 1.0) + (A - 1.0) * cosw0 - 2.0 * Math.sqrt(A) * alpha);
    a1 = 2.0 * ((A - 1.0) - (A + 1.0) * cosw0);
    a2 = (A + 1.0) - (A - 1.0) * cosw0 - 2.0 * Math.sqrt(A) * alpha;

    hb0 = (float) (b0 / a0);
    hb1 = (float) (b1 / a0);
    hb2 = (float) (b2 / a0);
    ha1 = (float) (a1 / a0);
    ha2 = (float) (a2 / a0);
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

    // Local variables for state to help JIT optimization
    float t_lx1 = lx1, t_lx2 = lx2, t_ly1 = ly1, t_ly2 = ly2;
    float t_hx1 = hx1, t_hx2 = hx2, t_hy1 = hy1, t_hy2 = hy2;

    for (int i = 0; i < length; i++) {
      float in = inputSum[i];

      // Process Low Shelf
      float lOut = lb0 * in + lb1 * t_lx1 + lb2 * t_lx2 - la1 * t_ly1 - la2 * t_ly2;
      t_lx2 = t_lx1;
      t_lx1 = in;
      t_ly2 = t_ly1;
      t_ly1 = lOut;

      // Process High Shelf
      float hOut = hb0 * lOut + hb1 * t_hx1 + hb2 * t_hx2 - ha1 * t_hy1 - ha2 * t_hy2;
      t_hx2 = t_hx1;
      t_hx1 = lOut;
      t_hy2 = t_hy1;
      t_hy1 = hOut;

      blockCache[i] = hOut * gain;
      if (buffer != null) buffer[offset + i] = blockCache[i];
    }

    lx1 = t_lx1;
    lx2 = t_lx2;
    ly1 = t_ly1;
    ly2 = t_ly2;
    hx1 = t_hx1;
    hx2 = t_hx2;
    hy1 = t_hy1;
    hy2 = t_hy2;

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = (systemTime == -1) ? -1 : systemTime + length - 1;
    if (length > 0) lastOut = blockCache[length - 1];
  }

  @Override
  protected float compute(float input, long systemTime) {
    float lOut = lb0 * input + lb1 * lx1 + lb2 * lx2 - la1 * ly1 - la2 * ly2;
    lx2 = lx1;
    lx1 = input;
    ly2 = ly1;
    ly1 = lOut;

    float hOut = hb0 * lOut + hb1 * hx1 + hb2 * hx2 - ha1 * hy1 - ha2 * hy2;
    hx2 = hx1;
    hx1 = lOut;
    hy2 = hy1;
    hy1 = hOut;

    return hOut;
  }
}
