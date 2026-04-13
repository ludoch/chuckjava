package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/** Wavetable: A basic wavetable oscillator / player. */
@doc("Wavetable oscillator. Plays back a fixed buffer of samples.")
public class Wavetable extends ChuckUGen {
  private float[] table;
  private double phase = 0.0;
  private double rate = 1.0;
  private boolean looping = true;
  private boolean playing = true;

  public Wavetable() {
    this.table = new float[256];
  }

  public void setTable(float[] table) {
    this.table = table;
  }

  public void rate(double r) {
    this.rate = r;
  }

  public void phase(double p) {
    this.phase = p;
  }

  public void loop(int l) {
    this.looping = (l != 0);
  }

  public void play(int p) {
    this.playing = (p != 0);
  }

  public void reset() {
    this.phase = 0.0;
    this.playing = true;
  }

  @Override
  protected float compute(float input, long systemTime) {
    if (!playing || table == null || table.length == 0) return 0.0f;

    // Linear interpolation
    int i0 = (int) phase;
    int i1 = (i0 + 1) % table.length;
    float f = (float) (phase - i0);

    float out = table[i0] + (table[i1] - table[i0]) * f;

    phase += rate;
    if (phase >= table.length) {
      if (looping) {
        phase %= table.length;
      } else {
        phase = 0;
        playing = false;
      }
    } else if (phase < 0) {
      if (looping) {
        phase = table.length + (phase % table.length);
      } else {
        phase = 0;
        playing = false;
      }
    }

    lastOut = out * gain;
    return lastOut;
  }
}
