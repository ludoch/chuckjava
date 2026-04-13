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
  private float mix = 0.5f;
  private float lastWetL = 0.0f;
  private float lastWetR = 0.0f;
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
    return mix;
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
  protected void computeStereo(float input, long systemTime) {
    // In ChucK, when a mono signal enters a stereo UGen,
    // it is duplicated. When stereo enters, we use both.
    float inL =
        getSources().size() > 0 ? getSources().get(0).getChannelLastOut(0, systemTime) : input;
    float inR =
        getSources().size() > 0 ? getSources().get(0).getChannelLastOut(1, systemTime) : input;

    // Feedback loop
    float wetL = delayL.tick(inL + lastWetL * gain, systemTime);
    float wetR = delayR.tick(inR + lastWetR * gain, systemTime);

    lastWetL = wetL;
    lastWetR = wetR;

    lastOutChannels[0] = inL * (1.0f - mix) + wetL * mix;
    lastOutChannels[1] = inR * (1.0f - mix) + wetR * mix;
  }
}
