package org.chuck.audio.fx;

import org.chuck.audio.ChuckUGen;

/** A linear-interpolating delay-line UGen. */
public class DelayL extends ChuckUGen {
  private final float[] buffer;
  private int writePos = 0;
  private double delaySamples;

  @SuppressWarnings("unused")
  private final float sampleRate;

  public DelayL(int maxDelaySamples) {
    this(maxDelaySamples, 44100.0f, true);
  }

  public DelayL(int maxDelaySamples, float sampleRate) {
    this(maxDelaySamples, sampleRate, true);
  }

  public DelayL(int maxDelaySamples, float sampleRate, boolean autoRegister) {
    super(autoRegister);
    this.buffer = new float[maxDelaySamples + 2]; // Extra space for interpolation
    this.delaySamples = maxDelaySamples - 1;
    this.sampleRate = sampleRate;
  }

  public void setDelay(double samples) {
    if (samples >= buffer.length - 1) samples = buffer.length - 2;
    if (samples < 0) samples = 0;
    this.delaySamples = samples;
  }

  public double delay(double samples) {
    setDelay(samples);
    return samples;
  }

  public double delay() {
    return delaySamples;
  }

  public double getDelay() {
    return delaySamples;
  }

  public void init(double delay, double max) {
    setDelay(delay);
  }

  @Override
  protected float compute(float input, long systemTime) {
    // Linear interpolation
    double readPos = writePos - delaySamples;
    while (readPos < 0) readPos += buffer.length;

    int i0 = (int) readPos;
    int i1 = (i0 + 1) % buffer.length;
    float frac = (float) (readPos - i0);

    float s0 = buffer[i0];
    float s1 = buffer[i1];
    float out = s0 + (s1 - s0) * frac;

    // Write current input to buffer
    buffer[writePos] = input;
    writePos = (writePos + 1) % buffer.length;

    return out;
  }
}
