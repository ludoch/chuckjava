package org.chuck.audio.fx;

import org.chuck.audio.util.StereoUGen;
import org.chuck.core.doc;

/**
 * Echo: A stereo-aware echo effect with feedback. In ChucK, .gain controls the feedback
 * coefficient.
 */
@doc("Stereo echo effect with feedback and mix control.")
public class Echo extends StereoUGen {
  private Delay delayL, delayR;
  private double mix = 0.5;
  private double lastWetL = 0.0;
  private double lastWetR = 0.0;
  private final float sampleRate;

  public Echo(int maxDelaySamples) {
    this(maxDelaySamples, 44100.0f);
  }

  public Echo(int maxDelaySamples, float sampleRate) {
    super();
    this.sampleRate = sampleRate;
    this.delayL = new Delay(maxDelaySamples, sampleRate, false);
    this.delayR = new Delay(maxDelaySamples, sampleRate, false);
    this.delayL.delay(0);
    this.delayR.delay(0);
  }

  @doc("Set the mix between dry and wet signal (0.0 to 1.0).")
  public void mix(float m) {
    this.mix = m;
  }

  public float mix() {
    return (float) mix;
  }

  @doc("Set the delay time in samples.")
  public void delay(double samples) {
    delayL.delay(samples);
    delayR.delay(samples);
  }

  public double delay() {
    return delayL.delay();
  }

  @doc("Set the feedback gain (alias for .gain).")
  public void feedback(float f) {
    this.gain = f;
  }

  @doc("Set the maximum delay time (resizes buffer).")
  public void max(double samples) {
    if (samples > delayL.getDelay()) {
      double currentDelay = delayL.delay();
      this.delayL = new Delay((int) samples, sampleRate, false);
      this.delayR = new Delay((int) samples, sampleRate, false);
      this.delayL.delay(currentDelay);
      this.delayR.delay(currentDelay);
    }
  }

  @Override
  protected void computeStereo(float left, float right, long systemTime) {
    // Feedback loop using separate channels
    double wetL = delayL.tick((float) (left + lastWetL * gain), systemTime);
    double wetR = delayR.tick((float) (right + lastWetR * gain), systemTime);

    lastWetL = wetL;
    lastWetR = wetR;

    lastOutChannels[0] = (float) (left * (1.0 - mix) + wetL * mix);
    lastOutChannels[1] = (float) (right * (1.0 - mix) + wetR * mix);
  }

  @Override
  protected void computeStereo(float input, long systemTime) {
    // Legacy fallback handled by computeStereo(left, right)
  }
}
