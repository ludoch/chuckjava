package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/**
 * Morphing Wavetable Oscillator. Blends between multiple single-cycle waveforms based on an index
 * parameter.
 */
@doc("Morphing Wavetable Oscillator with 2D wave blending.")
public class MorphingWavetable extends ChuckUGen {
  private float[][] tables;
  private float sampleRate;

  private double phase = 0.0;
  private float freq = 440.0f;
  private float index = 0.0f;

  public MorphingWavetable() {
    this(org.chuck.core.ChuckVM.CURRENT_VM.get().getSampleRate());
  }

  public MorphingWavetable(float sampleRate) {
    this.sampleRate = sampleRate;
    // Default to two simple tables if none provided: Sine and Saw
    this.tables = new float[2][256];
    for (int i = 0; i < 256; i++) {
      this.tables[0][i] = (float) Math.sin(2.0 * Math.PI * i / 256.0); // Sine
      this.tables[1][i] = (float) (2.0 * (i / 256.0) - 1.0); // Saw
    }
  }

  public void setTables(float[][] tables) {
    if (tables != null && tables.length > 0 && tables[0].length > 0) {
      this.tables = tables;
      // Re-clamp index to new table length
      index(this.index);
    }
  }

  public double freq(double f) {
    this.freq = (float) f;
    return this.freq;
  }

  public double freq() {
    return freq;
  }

  public double index(double idx) {
    // Clamp between 0 and (number of tables - 1)
    this.index = (float) Math.max(0.0, Math.min(tables.length - 1.0f, idx));
    return this.index;
  }

  public double index() {
    return index;
  }

  public void phase(double p) {
    this.phase = p;
  }

  @Override
  protected float compute(float input, long systemTime) {
    int numTables = tables.length;
    int tableLen = tables[0].length;

    // Calculate phase increment
    double rate = (freq * tableLen) / sampleRate;

    // Waveform interpolation (Z-axis)
    int t0 = (int) index;
    int t1 = Math.min(t0 + 1, numTables - 1);
    float tFrac = index - t0;

    // Phase interpolation (X-axis)
    int i0 = (int) phase;
    int i1 = (i0 + 1) % tableLen;
    float pFrac = (float) (phase - i0);

    // Read from table 0
    float v00 = tables[t0][i0];
    float v01 = tables[t0][i1];
    float w0 = v00 + (v01 - v00) * pFrac;

    // Read from table 1
    float v10 = tables[t1][i0];
    float v11 = tables[t1][i1];
    float w1 = v10 + (v11 - v10) * pFrac;

    // Morph between the two
    float out = w0 + (w1 - w0) * tFrac;

    // Advance phase
    phase += rate;
    if (phase >= tableLen) {
      phase %= tableLen;
    } else if (phase < 0) {
      phase = tableLen + (phase % tableLen);
    }

    return out * gain;
  }
}
