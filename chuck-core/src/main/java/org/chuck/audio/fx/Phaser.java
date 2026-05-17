package org.chuck.audio.fx;

import org.chuck.audio.ChuckUGen;

/** A phaser effect using a series of all-pass filters modulated by an internal LFO. */
public class Phaser extends ChuckUGen {
  private final int stages;
  private final double[] allpassA; // Filter coefficients (frequency-related)
  private final double[] allpassMemory;
  private double lfoPhase = 0.0;
  private double lfoRate = 0.25;
  private double modDepth = 0.5;
  private double feedback = 0.0;
  private double mix = 0.5;
  private double sampleRate;
  private double fbOut = 0.0;

  public Phaser(float sampleRate) {
    this(sampleRate, 6); // 6-stage phaser
  }

  public Phaser(float sampleRate, int stages) {
    this.sampleRate = sampleRate;
    this.stages = stages;
    this.allpassA = new double[stages];
    this.allpassMemory = new double[stages];
  }

  public void setModFreq(double freq) {
    this.lfoRate = freq;
  }

  public void setModDepth(double depth) {
    this.modDepth = Math.max(0.0, Math.min(1.0, depth));
  }

  public void setFeedback(double fb) {
    this.feedback = Math.max(-0.95, Math.min(0.95, fb));
  }

  public void setMix(double mix) {
    this.mix = mix;
  }

  @Override
  protected float compute(float input, long systemTime) {
    lfoPhase = (lfoPhase + lfoRate / sampleRate) % 1.0;
    // LFO: 0..1 maps to freq sweep
    double lfo = (Math.sin(lfoPhase * 2.0 * Math.PI) + 1.0) * 0.5;
    // Map to cutoff frequency range 200Hz-4000Hz -> coefficient a
    double minFreq = 200.0;
    double maxFreq = 4000.0;
    double freq = minFreq * Math.pow(maxFreq / minFreq, lfo * modDepth);
    double a =
        (1.0 - Math.sin(2.0 * Math.PI * freq / sampleRate))
            / (1.0 + Math.sin(2.0 * Math.PI * freq / sampleRate));

    double x = input + feedback * fbOut;
    for (int i = 0; i < stages; i++) {
      double y = a * (x - allpassMemory[i]);
      double out = y + allpassMemory[i];
      allpassMemory[i] = y + x;
      x = out;
    }
    fbOut = x;

    return (float) (input * (1.0 - mix) + x * mix);
  }
}
