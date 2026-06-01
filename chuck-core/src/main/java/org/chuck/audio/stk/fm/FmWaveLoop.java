package org.chuck.audio.stk.fm;

/**
 * Faithful port of STK's WaveLoop over an in-memory rawwave table (see {@link Rawwaves}). Plain DSP
 * object (not a graph UGen): the owning instrument ticks it directly, exactly as STK's FM voices
 * tick their {@code waves[]} members.
 *
 * <p>The table is normalized to peak 1.0 on construction (STK loads raw waves with {@code
 * doNormalize = TRUE}, so {@code scaleToOne} stays 1.0). Playback is a looping linear interpolation
 * with an appended loop sample ({@code data[fileSize] = data[0]}), matching WaveLoop::tickFrame.
 */
public final class FmWaveLoop {
  private final float[] data; // fileSize + 1 entries, normalized to peak 1.0
  private final double fileSize;
  private final double sampleRate;
  private double time = 0.0;
  private double rate = 1.0;
  private double phaseOffset = 0.0;

  public FmWaveLoop(float[] table, double sampleRate) {
    this.sampleRate = sampleRate;
    int n = table.length;
    this.fileSize = n;
    this.data = new float[n + 1];
    double max = 0.0;
    for (float v : table) max = Math.max(max, Math.abs(v));
    double norm = (max > 0.0) ? 1.0 / max : 1.0;
    for (int i = 0; i < n; i++) data[i] = (float) (table[i] * norm);
    data[n] = data[0]; // loop sample
  }

  /** WaveLoop::setFrequency — looping frequency: rate = fileSize * f / sampleRate. */
  public void setFrequency(double frequency) {
    rate = fileSize * frequency / sampleRate;
  }

  /** WaveLoop::addPhaseOffset — phase offset in cycles (1.0 == fileSize). */
  public void addPhaseOffset(double angle) {
    phaseOffset = fileSize * angle;
  }

  /** WaveLoop::tickFrame (mono): wrap time, apply phase offset, linear-interpolate, advance. */
  public double tick() {
    while (time < 0.0) time += fileSize;
    while (time >= fileSize) time -= fileSize;

    double tyme;
    if (phaseOffset != 0.0) {
      tyme = time + phaseOffset;
      while (tyme < 0.0) tyme += fileSize;
      while (tyme >= fileSize) tyme -= fileSize;
    } else {
      tyme = time;
    }

    int index = (int) tyme; // always linear interpolation in WaveLoop
    double alpha = tyme - index;
    double out = data[index];
    out += alpha * (data[index + 1] - out);

    time += rate;
    return out;
  }
}
