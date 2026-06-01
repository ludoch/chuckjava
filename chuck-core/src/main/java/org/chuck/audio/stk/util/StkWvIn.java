package org.chuck.audio.stk.util;

/**
 * STK WvIn for in-memory rawwave tables, finite (non-looping) playback — ported from ugen_stk.cpp.
 * Tables are normalized to peak 1.0 on load (WvIn doNormalize=TRUE, so scaleToOne stays 1.0). Used
 * for the Mandolin body excitation and the Moog attack transient.
 */
public final class StkWvIn {
  private final double[] data; // fileSize + 1 (data[fileSize] = data[0])
  private final int fileSize;
  private final double sampleRate;
  private double time = 0.0;
  private double rate;
  private boolean interpolate = true;
  private boolean finished = false;

  public StkWvIn(float[] table, double sampleRate) {
    this.sampleRate = sampleRate;
    int n = table.length;
    fileSize = n;
    data = new double[n + 1];
    double max = 0.0;
    for (float v : table) max = Math.max(max, Math.abs(v));
    double norm = (max > 0.0) ? 1.0 / max : 1.0;
    for (int i = 0; i < n; i++) data[i] = table[i] * norm;
    data[n] = data[0];
    rate = 22050.0 / sampleRate; // STK special-rawwave default
    interpolate = (rate % 1.0) != 0.0;
  }

  public void reset() {
    time = 0.0;
    finished = false;
  }

  public void setRate(double aRate) {
    rate = aRate;
    if ((rate < 0) && (time == 0.0)) time += rate + fileSize;
    interpolate = (rate % 1.0) != 0.0;
  }

  public int getSize() {
    return fileSize;
  }

  public double sampleRate() {
    return sampleRate;
  }

  public boolean isFinished() {
    return finished;
  }

  public double tick() {
    if (finished) return 0.0;
    double tyme = time;
    int index = (int) tyme;
    double out;
    if (interpolate) {
      double alpha = tyme - index;
      out = data[index];
      out += alpha * (data[index + 1] - out);
    } else {
      out = data[index];
    }
    time += rate;
    if (time < 0.0 || time >= fileSize) finished = true;
    return out;
  }
}
