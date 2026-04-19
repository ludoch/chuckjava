package org.chuck.audio.fx;

import org.chuck.audio.ChuckUGen;

/** A stereo Ping-Pong Delay effect. Alternates echoes between the Left and Right channels. */
public class PingPongDelay extends ChuckUGen {

  private final DelayL delayL;
  private final DelayL delayR;
  private final float sampleRate;

  private float feedback = 0.5f;
  private float time = 0.5f; // Seconds

  public PingPongDelay() {
    this(org.chuck.core.ChuckVM.CURRENT_VM.get().getSampleRate());
  }

  public PingPongDelay(float sampleRate) {
    super(true);
    this.sampleRate = sampleRate;

    // Create two independent delay lines
    // Max 4 seconds = 4 * sampleRate
    int maxSamples = (int) (4.0 * sampleRate);
    delayL = new DelayL(maxSamples, sampleRate);
    delayR = new DelayL(maxSamples, sampleRate);

    updateDelays();
  }

  public int getNumChannels() {
    return 2; // Stereo output
  }

  public double time(double t) {
    this.time = (float) Math.max(0.001, t);
    updateDelays();
    return this.time;
  }

  public double time() {
    return time;
  }

  public double feedback(double fb) {
    this.feedback = (float) Math.max(0.0, Math.min(0.99, fb));
    return this.feedback;
  }

  public double feedback() {
    return feedback;
  }

  private void updateDelays() {
    // Ping-pong offsets the right channel by half the delay time
    delayL.delay(time * sampleRate);
    delayR.delay(time * 1.5 * sampleRate);
  }

  @Override
  public void tick(float[] buffer, int offset, int length, long systemTime) {
    // Stereo output format: L, R, L, R, ...
    if (blockCache == null || blockCache.length < length * 2) {
      blockCache = new float[length * 2];
    }

    // Process mono input into stereo ping-pong
    for (int i = 0; i < length; i++) {
      float in = 0.0f;
      // Sum inputs
      if (getNumSources() > 0) {
        for (ChuckUGen src : sources) {
          in += src.getLastOut(); // Simplified per-sample processing for this FX
          src.tick(1); // Advance source
        }
      }

      // Left delay line reads from Right delay output + input
      float outL = delayL.compute(in + delayR.getLastOut() * feedback, systemTime);

      // Right delay line reads from Left delay output
      float outR = delayR.compute(outL * feedback, systemTime);

      blockCache[i * 2] = outL * gain;
      blockCache[i * 2 + 1] = outR * gain;

      if (buffer != null) {
        buffer[offset + i * 2] = blockCache[i * 2];
        buffer[offset + i * 2 + 1] = blockCache[i * 2 + 1];
      }
    }

    if (length > 0) lastOut = blockCache[(length - 1) * 2]; // Return L channel for mono get
  }

  @Override
  protected float compute(float input, long systemTime) {
    // Single sample compute (returns L channel for mono compatibility)
    float outL = delayL.compute(input + delayR.getLastOut() * feedback, systemTime);
    delayR.compute(outL * feedback, systemTime);
    lastOut = outL;
    return outL;
  }
}
