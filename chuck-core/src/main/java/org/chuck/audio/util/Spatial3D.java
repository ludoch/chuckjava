package org.chuck.audio.util;

import org.chuck.audio.filter.OnePole;
import org.chuck.audio.fx.Delay;
import org.chuck.core.doc;

/**
 * Spatial3D: Binaural 3D panner using ITD, ILD, and Head Shadowing. Positions a mono source in 3D
 * space for stereo headphones.
 */
@doc("Binaural 3D panner for headphones. Uses ITD and ILD models.")
public class Spatial3D extends MultiChannelUGen {
  private float azimuth = 0.0f; // -180 to 180 degrees
  private float elevation = 0.0f; // -90 to 90 degrees
  private float distance = 1.0f; // Normalized distance

  // Per-ear processing chains
  private final Delay delayL;
  private final Delay delayR;
  private final OnePole shadowL;
  private final OnePole shadowR;

  private static final float HEAD_RADIUS = 0.0875f; // approx 8.75cm
  private static final float SPEED_OF_SOUND = 343.0f;

  public Spatial3D(float sampleRate) {
    super(2); // Stereo output
    this.delayL = new Delay(1024);
    this.delayR = new Delay(1024);
    this.shadowL = new OnePole(sampleRate);
    this.shadowR = new OnePole(sampleRate);

    // Default: centered
    updateParameters();
  }

  @doc("Set azimuth in degrees (-180 to 180). 0 is front, 90 is right.")
  public void azimuth(float a) {
    this.azimuth = a;
    updateParameters();
  }

  @doc("Set elevation in degrees (-90 to 90).")
  public void elevation(float e) {
    this.elevation = e;
    updateParameters();
  }

  @doc("Set distance (normalized). 1.0 is default.")
  public void distance(float d) {
    this.distance = Math.max(0.01f, d);
    updateParameters();
  }

  private void updateParameters() {
    // 1. Calculate ITD using Woodworth's formula
    double rad = Math.toRadians(azimuth);
    double itd = (HEAD_RADIUS / SPEED_OF_SOUND) * (Math.sin(rad) + rad);

    // Convert to samples
    float itdSamples = (float) (itd * 44100.0); // Use a fixed rate for param calc or fetch it

    if (itdSamples >= 0) {
      delayL.delay(0);
      delayR.delay(itdSamples);
    } else {
      delayL.delay(-itdSamples);
      delayR.delay(0);
    }

    // 2. ILD and Shadowing (Simplified)
    float cosAz = (float) Math.cos(rad);
    float ildL = 1.0f - 0.5f * (1.0f - cosAz);
    float ildR = 1.0f - 0.5f * (1.0f + cosAz);

    // Distance attenuation (inverse square law)
    float distAtten = 1.0f / (distance * distance);

    shadowL.gain(ildL * distAtten);
    shadowR.gain(ildR * distAtten);

    // Shadow filter (simple lowpass for the "far" ear)
    // frequency decreases as ear is shadowed
    float shadowFreqL = 5000.0f + 10000.0f * (1.0f - ildL);
    float shadowFreqR = 5000.0f + 10000.0f * (1.0f - ildR);
    shadowL.freq(shadowFreqL);
    shadowR.freq(shadowFreqR);
  }

  @Override
  protected void computeMulti(float input, long systemTime) {
    // Process Left Ear
    float sL = delayL.tick(input, systemTime);
    lastOutChannels[0] = shadowL.tick(sL, systemTime) * gain;

    // Process Right Ear
    float sR = delayR.tick(input, systemTime);
    lastOutChannels[1] = shadowR.tick(sR, systemTime) * gain;

    lastOut = lastOutChannels[0];
  }
}
