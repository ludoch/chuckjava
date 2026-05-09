package org.chuck.audio.fx;

import org.chuck.audio.ChuckUGen;

/**
 * Unified ModFX UGen that switches between algorithms: NONE, CHORUS, FLANGER, PHASER,
 * WARBLER, DIMENSION, GRAIN, CHORUS_STEREO.
 *
 * <p>Each track gets one instance. Parameters (rate, depth, feedback, offset) are set
 * per-track, and the algorithm is selected via {@link #setType(int)}.
 */
public class ModFxUnit extends ChuckUGen {
  private final float sampleRate;
  private int type = 0; // 0=NONE, 1=CHORUS, 2=FLANGER, 3=PHASER, 4=WARBLER, 5=DIMENSION, 6=GRAIN, 7=CHORUS_STEREO

  // CHORUS/FLANGER shared resources
  private final DelayL delayLine;
  private double lfoPhase = 0.0;
  private double lfoRate = 0.3;
  private double delayModDepth = 0.3;
  private double feedback = 0.0;
  private double mix = 0.5;
  private double baseDelaySamples;
  private double lastWet = 0.0;
  private int maxDelaySamples;

  // PHASER
  private static final int PHASER_STAGES = 6;
  private final double[] apA = new double[PHASER_STAGES];
  private final double[] apMem = new double[PHASER_STAGES];
  private double phaserFbOut = 0.0;

  // DIMENSION
  private final double[] dimPhase = new double[]{0.0, 2.0/3.0, 4.0/3.0};
  private static final double[] DIM_DELAYS = {0.008, 0.014, 0.020};

  // GRAIN
  private final float[] grainBuffer;
  private int grainWritePos = 0;
  private double grainPhase = 0.0;
  private static final int GRAIN_RING_MS = 500;
  private static final int MAX_GRAINS = 8;

  // OFFSET parameter (extra delay offset for chorus/flanger)
  private double offset = 0.0;

  // WARBLER persistent state
  private double warbler1 = 0.0, warbler2 = 0.0;

  public ModFxUnit(float sampleRate) {
    this.sampleRate = sampleRate;
    this.maxDelaySamples = (int) (0.060f * sampleRate) + 4;
    this.delayLine = new DelayL(maxDelaySamples);
    this.baseDelaySamples = 0.025 * sampleRate; // 25ms default for chorus
    delayLine.setDelay(baseDelaySamples);
    int ringLen = (int) (GRAIN_RING_MS / 1000.0 * sampleRate) + 4;
    this.grainBuffer = new float[ringLen];
  }

  public void setType(int type) { this.type = type; }
  public void setModFreq(double freq) { this.lfoRate = freq; }
  public void setModDepth(double depth) { this.delayModDepth = Math.max(0.0, Math.min(1.0, depth)); }
  public void setFeedback(double fb) { this.feedback = Math.max(-0.95, Math.min(0.95, fb)); }
  public void setMix(double mix) { this.mix = mix; }
  public void setOffset(double offset) { this.offset = Math.max(0.0, Math.min(1.0, offset)); }

  @Override
  protected float compute(float input, long systemTime) {
    switch (type) {
      case 0: return input; // NONE (passthrough)
      case 1: return computeChorus(input, systemTime);
      case 2: return computeFlanger(input, systemTime);
      case 3: return computePhaser(input, systemTime);
      case 4: return computeWarbler(input, systemTime);
      case 5: return computeDimension(input, systemTime);
      case 6: return computeGrain(input, systemTime);
      case 7: return computeChorusStereo(input, systemTime);
      default: return input;
    }
  }

  private float computeChorus(float input, long systemTime) {
    lfoPhase = (lfoPhase + lfoRate / sampleRate) % 1.0;
    double lfo = Math.sin(lfoPhase * 2.0 * Math.PI);
    double extraDelay = offset * 0.015 * sampleRate;
    double delay = (baseDelaySamples + extraDelay) * (1.0 + delayModDepth * lfo * 0.5);
    delayLine.setDelay(Math.max(2.0, Math.min(maxDelaySamples - 2, delay)));
    float wet = delayLine.tick(input + (float)(lastWet * feedback * 0.5), systemTime);
    lastWet = wet;
    return input * (1.0f - (float) mix) + wet * (float) mix;
  }

  private float computeFlanger(float input, long systemTime) {
    lfoPhase = (lfoPhase + lfoRate / sampleRate) % 1.0;
    double lfo = Math.sin(lfoPhase * 2.0 * Math.PI);
    double base = 0.001 * sampleRate; // 1ms
    double maxD = 0.010 * sampleRate; // 10ms
    double extraOffset = offset * 0.005 * sampleRate;
    double delay = base + extraOffset + (maxD - base) * delayModDepth * (lfo * 0.5 + 0.5);
    delayLine.setDelay(Math.max(2.0, Math.min(maxDelaySamples - 2, delay)));
    float wet = delayLine.tick(input + (float)(lastWet * feedback), systemTime);
    lastWet = wet;
    return input * (1.0f - (float) mix) + wet * (float) mix;
  }

  private float computePhaser(float input, long systemTime) {
    lfoPhase = (lfoPhase + lfoRate / sampleRate) % 1.0;
    double lfo = (Math.sin(lfoPhase * 2.0 * Math.PI) + 1.0) * 0.5;
    double minFreq = 200.0;
    double maxFreq = 4000.0;
    double freq = minFreq * Math.pow(maxFreq / minFreq, lfo * delayModDepth);
    double a = (1.0 - Math.sin(2.0 * Math.PI * freq / sampleRate))
             / (1.0 + Math.sin(2.0 * Math.PI * freq / sampleRate));

    double x = input + feedback * phaserFbOut;
    for (int i = 0; i < PHASER_STAGES; i++) {
      double y = a * (x - apMem[i]);
      double out = y + apMem[i];
      apMem[i] = y + x;
      x = out;
    }
    phaserFbOut = x;
    return (float) (input * (1.0 - mix) + x * mix);
  }

  private float computeWarbler(float input, long systemTime) {
    lfoPhase = (lfoPhase + lfoRate / sampleRate) % 1.0;
    warbler1 += (Math.random() - 0.5) * 0.1;
    warbler1 *= 0.99;
    warbler2 += (warbler1 - warbler2) * 0.2;
    double w2clamped = Math.max(-1.0, Math.min(1.0, warbler2));
    double combined = 0.7 * Math.sin(lfoPhase * 2.0 * Math.PI) + 0.3 * w2clamped;
    double extraOffset = offset * 0.015 * sampleRate;
    double base = 0.015 * sampleRate + extraOffset;
    double mod = combined * delayModDepth * 0.02 * sampleRate;
    delayLine.setDelay(Math.max(2.0, Math.min(maxDelaySamples - 2, base + mod)));
    float wet = delayLine.tick(input + (float)(lastWet * feedback * 0.707), systemTime);
    lastWet = wet;
    return input * (1.0f - (float) mix) + wet * (float) mix;
  }

  private float computeDimension(float input, long systemTime) {
    double phase = lfoRate / sampleRate;
    float wetSum = 0.0f;
    for (int i = 0; i < 3; i++) {
      dimPhase[i] = (dimPhase[i] + phase) % 1.0;
      double lfo = dimPhase[i] < 0.5 ? 4.0 * dimPhase[i] - 1.0 : 3.0 - 4.0 * dimPhase[i];
      double baseDelay = DIM_DELAYS[i] * sampleRate;
      double delayMod = lfo * delayModDepth * 0.005 * sampleRate;
      int maxD = (int) (0.050 * sampleRate) + 2;
      delayLine.setDelay(Math.max(2.0, Math.min(maxD, baseDelay + delayMod)));
      wetSum += delayLine.tick(input, systemTime);
    }
    float wet = wetSum / 3.0f;
    return input * (1.0f - (float) mix) + wet * (float) mix;
  }

  private float computeGrain(float input, long systemTime) {
    grainBuffer[grainWritePos] = input + grainBuffer[grainWritePos] * (float) feedback;
    grainWritePos = (grainWritePos + 1) % grainBuffer.length;

    grainPhase += Math.max(0.05, delayModDepth * 0.3) * 0.05;
    float wetSum = 0.0f;
    int nGrains = Math.max(1, (int) (delayModDepth * MAX_GRAINS));
    for (int g = 0; g < nGrains; g++) {
      int grainLen = (int) ((0.005 + lfoRate * 0.195) * sampleRate);
      if (grainLen < 4) grainLen = 4;
      double window = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * (grainPhase % 1.0));
      int offset = (g * 137) % grainBuffer.length;
      int readPos = (grainWritePos - offset + grainBuffer.length) % grainBuffer.length;
      wetSum += grainBuffer[readPos] * (float) window;
    }
    float wet = wetSum / nGrains;
    return input * (1.0f - (float) mix) + wet * (float) mix;
  }

  private float computeChorusStereo(float input, long systemTime) {
    lfoPhase = (lfoPhase + lfoRate / sampleRate) % 1.0;
    double lfoL = Math.sin(lfoPhase * 2.0 * Math.PI);
    double lfoR = Math.sin((lfoPhase + 1.0/3.0) * 2.0 * Math.PI);
    double extraOffset = offset * 0.015 * sampleRate;
    double base = 0.025 * sampleRate + extraOffset;
    double delayL = base * (1.0 + delayModDepth * lfoL);
    double delayR = base * (1.0 + delayModDepth * lfoR * 1.3);
    delayLine.setDelay(Math.max(2.0, Math.min(maxDelaySamples - 2, delayL)));
    float wetL = delayLine.tick(input + (float)(lastWet * feedback * 0.707), systemTime);
    delayLine.setDelay(Math.max(2.0, Math.min(maxDelaySamples - 2, delayR)));
    float wetR = delayLine.tick(input + (float)(lastWet * feedback * 0.707), systemTime);
    lastWet = (wetL + wetR) * 0.5f;
    return input * (1.0f - (float) mix) + (float)(lastWet * mix);
  }
}
