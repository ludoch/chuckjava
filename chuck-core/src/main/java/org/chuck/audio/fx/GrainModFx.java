package org.chuck.audio.fx;

import org.chuck.audio.ChuckUGen;

/**
 * GRAIN ModFX: granular effect with grain size and density parameters.
 * Captures input into a ring buffer and overlaps short grains at varying positions.
 */
public class GrainModFx extends ChuckUGen {
  private final float[] ringBuffer;
  private int writePos = 0;
  private double sampleRate;
  private double grainSize = 0.05;   // in seconds
  private double grainDensity = 0.5; // 0-1
  private double grainFeedback = 0.0;
  private double mix = 0.5;
  private double grainPhase = 0.0;
  private static final int MAX_GRAINS = 8;
  private static final int RING_SIZE_MS = 500; // 500ms ring buffer

  public GrainModFx(float sampleRate) {
    this.sampleRate = sampleRate;
    int ringLen = (int) (RING_SIZE_MS / 1000.0 * sampleRate) + 4;
    this.ringBuffer = new float[ringLen];
  }

  public void setModFreq(double freq) {
    // Map mod rate to grain density multiplier
    this.grainDensity = Math.min(1.0, freq * 0.5 + 0.1);
  }

  public void setModDepth(double depth) {
    // Map mod depth to grain size (5ms - 200ms)
    this.grainSize = 0.005 + depth * 0.195;
  }

  public void setFeedback(double fb) { this.grainFeedback = Math.max(0.0, Math.min(0.9, fb)); }

  public void setMix(double mix) { this.mix = mix; }

  @Override
  protected float compute(float input, long systemTime) {
    // Write to ring buffer
    ringBuffer[writePos] = input + ringBuffer[writePos] * (float) grainFeedback;
    writePos = (writePos + 1) % ringBuffer.length;

    grainPhase += grainDensity * 0.05; // grain trigger rate
    float wetSum = 0.0f;
    int activeGrains = 0;

    int nGrains = (int) (grainDensity * MAX_GRAINS);
    if (nGrains < 1) nGrains = 1;

    for (int g = 0; g < nGrains; g++) {
      // Each grain reads from a different position in the ring buffer
      int grainLen = (int) (grainSize * sampleRate);
      if (grainLen < 4) grainLen = 4;
      // Window each grain with a linear envelope
      double window = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * (grainPhase % 1.0));
      // Read from ring buffer at varying offsets
      int offset = (g * 137) % ringBuffer.length; // prime spread
      int readPos = (writePos - offset + ringBuffer.length) % ringBuffer.length;
      float sample = ringBuffer[readPos];
      wetSum += sample * (float) window;
      activeGrains++;
    }

    float wet = activeGrains > 0 ? wetSum / activeGrains : 0.0f;
    return input * (1.0f - (float) mix) + wet * (float) mix;
  }
}
