package org.chuck.audio.fx;

import org.chuck.audio.ChuckUGen;

/** An echo effect with feedback. */
public class Echo extends ChuckUGen {
  private final Delay delayLine;
  private float mix = 0.5f;
  private float lastWet = 0.0f;

  public Echo(int maxDelaySamples) {
    this.delayLine = new Delay(maxDelaySamples);
  }

  public void setDelay(int samples) {
    delayLine.setDelay(samples);
  }

  public void setMix(float mix) {
    this.mix = mix;
  }

  public void setMax(double samples) {
    // Echo current implementation wraps a fixed-size Delay.
    // In a full implementation, we'd resize the buffer.
    // For now, we just accept the call.
  }

  @Override
  protected float compute(float input, long systemTime) {
    // Feedback loop: input + (last wet output * gain)
    float dry = input;
    float wet = delayLine.tick(input + lastWet * gain, systemTime);
    lastWet = wet;

    return dry * (1.0f - mix) + wet * mix;
  }
}
