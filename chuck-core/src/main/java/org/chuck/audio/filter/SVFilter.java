package org.chuck.audio.filter;

import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/**
 * State Variable Filter (SVF) with continuous morphing. Morphs from Low-Pass (0.0) to Band-Pass
 * (0.5) to High-Pass (1.0). Implements a Zero-Delay Feedback (ZDF) topology with internal tanh
 * saturation.
 */
@doc("State Variable Filter (ZDF) with LP/BP/HP morphing.")
public class SVFilter extends ChuckUGen {
  private float sampleRate;
  private float cutoff = 1000.0f;
  private float resonance = 0.5f; // Q factor
  private float morph = 0.0f; // 0=LP, 0.5=BP, 1.0=HP

  // Filter state
  private float ic1eq = 0.0f;
  private float ic2eq = 0.0f;

  public SVFilter() {
    this(org.chuck.core.ChuckVM.CURRENT_VM.get().getSampleRate());
  }

  public SVFilter(float sampleRate) {
    this.sampleRate = sampleRate;
  }

  public double freq(double f) {
    this.cutoff = (float) Math.max(10.0, Math.min(sampleRate / 2.0, f));
    return this.cutoff;
  }

  public double freq() {
    return cutoff;
  }

  public double Q(double q) {
    this.resonance = (float) Math.max(0.1, q);
    return this.resonance;
  }

  public double Q() {
    return resonance;
  }

  public double morph(double m) {
    this.morph = (float) Math.max(0.0, Math.min(1.0, m));
    return this.morph;
  }

  public double morph() {
    return morph;
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

    // Sum inputs
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

    // Process filter
    // Morph coefficients
    float m = this.morph;
    float cLow = m <= 0.5f ? 1.0f - 2.0f * m : 0.0f;
    float cBand = m <= 0.5f ? 2.0f * m : 1.0f - 2.0f * (m - 0.5f);
    float cHigh = m <= 0.5f ? 0.0f : 2.0f * (m - 0.5f);

    // ZDF parameters (double sampled, so we use sampleRate * 2)
    float g = (float) Math.tan(Math.PI * cutoff / (sampleRate * 2.0f));
    float R = 1.0f / (2.0f * resonance);
    float denom = 1.0f / (1.0f + 2.0f * R * g + g * g);

    float l_ic1eq = ic1eq;
    float l_ic2eq = ic2eq;

    for (int i = 0; i < length; i++) {
      float in = inputSum[i];
      float out = 0.0f;

      // Double sampling loop
      for (int step = 0; step < 2; step++) {
        float hp = (in - 2.0f * R * l_ic1eq - g * l_ic1eq - l_ic2eq) * denom;
        float bp = l_ic1eq + g * hp;

        // Tanh saturation on bandpass state
        bp = (float) Math.tanh(bp);

        float lp = l_ic2eq + g * bp;

        l_ic1eq = 2.0f * bp - l_ic1eq;
        l_ic2eq = 2.0f * lp - l_ic2eq;

        if (step == 1) { // Take output on the second step
          out = cLow * lp + cBand * bp + cHigh * hp;
        }
      }

      blockCache[i] = out * gain;
      if (buffer != null) buffer[offset + i] = blockCache[i];
    }

    ic1eq = l_ic1eq;
    ic2eq = l_ic2eq;

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = (systemTime == -1) ? -1 : systemTime + length - 1;
    if (length > 0) lastOut = blockCache[length - 1];
  }

  @Override
  protected float compute(float input, long systemTime) {
    float m = this.morph;
    float cLow = m <= 0.5f ? 1.0f - 2.0f * m : 0.0f;
    float cBand = m <= 0.5f ? 2.0f * m : 1.0f - 2.0f * (m - 0.5f);
    float cHigh = m <= 0.5f ? 0.0f : 2.0f * (m - 0.5f);

    float g = (float) Math.tan(Math.PI * cutoff / (sampleRate * 2.0f));
    float R = 1.0f / (2.0f * resonance);
    float denom = 1.0f / (1.0f + 2.0f * R * g + g * g);

    float out = 0.0f;
    for (int step = 0; step < 2; step++) {
      float hp = (input - 2.0f * R * ic1eq - g * ic1eq - ic2eq) * denom;
      float bp = ic1eq + g * hp;
      bp = (float) Math.tanh(bp);
      float lp = ic2eq + g * bp;

      ic1eq = 2.0f * bp - ic1eq;
      ic2eq = 2.0f * lp - ic2eq;

      if (step == 1) {
        out = cLow * lp + cBand * bp + cHigh * hp;
      }
    }
    return out;
  }
}
