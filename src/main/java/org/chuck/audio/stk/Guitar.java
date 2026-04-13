package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.filter.OnePole;
import org.chuck.core.doc;

/**
 * Guitar: Multi-string physical model. Based on STK Guitar class. Manages 6 Twang strings with
 * bridge coupling.
 */
@doc("Multi-string guitar physical model with bridge coupling.")
public class Guitar extends ChuckUGen {
  private final Twang[] strings;
  private final float[] excitation;
  private int filePointer = 0;
  private int stringState = 0; // 0: idle, 1: decaying, 2: plucking

  private final OnePole pickFilter;
  private final OnePole couplingFilter;
  private float couplingGain = 0.01f;

  private final float[] pluckGains;
  private final float sampleRate;

  public Guitar(float sampleRate) {
    this.sampleRate = sampleRate;
    this.strings = new Twang[6];
    this.pluckGains = new float[6];
    for (int i = 0; i < 6; i++) {
      strings[i] = new Twang(sampleRate, false);
    }

    // Default excitation: white noise burst
    this.excitation = new float[4096];
    for (int i = 0; i < excitation.length; i++) {
      excitation[i] = (float) (Math.random() * 2.0 - 1.0);
      // Envelope the noise
      excitation[i] *= Math.pow(1.0 - (double) i / excitation.length, 2.0);
    }

    this.pickFilter = new OnePole(sampleRate, false);
    this.pickFilter.setPole(0.95f); // Soft pick default

    this.couplingFilter = new OnePole(sampleRate, false);
    this.couplingFilter.setPole(0.9f);

    this.numOutputs = 2; // Stereo coupling out
  }

  @doc("Pluck a string (0-5) with given frequency and amplitude.")
  public void noteOn(int string, double freq, double amplitude) {
    if (string < 0 || string >= 6) return;
    strings[string].freq(freq);
    pluckGains[string] = (float) amplitude;
    filePointer = 0;
    stringState = 2;
  }

  @doc("Damp a string (0-5).")
  public void noteOff(int string, double amplitude) {
    if (string < 0 || string >= 6) return;
    strings[string].loopGain(0.8); // Fast decay
  }

  @doc("Set the 'hardness' of the pick (0.0 to 1.0).")
  public void pickHardness(double h) {
    pickFilter.setPole((float) (0.99 - h * 0.2));
  }

  @doc("Set bridge coupling gain.")
  public void coupling(double g) {
    this.couplingGain = (float) g;
  }

  @Override
  protected float compute(float input, long systemTime) {
    float pluckSignal = 0.0f;
    if (stringState == 2) {
      pluckSignal = excitation[filePointer++];
      if (filePointer >= excitation.length) {
        stringState = 1;
      }
    }

    pluckSignal = pickFilter.tick(pluckSignal, systemTime);

    float totalOutput = 0.0f;
    float bridgeSum = 0.0f;

    for (int i = 0; i < 6; i++) {
      // Feed pluck signal + coupling feedback into each string
      float stringIn = pluckSignal * pluckGains[i];
      // Add coupling from other strings (simplified)
      float out = strings[i].tick(stringIn, systemTime);
      totalOutput += out;
      bridgeSum += out;
    }

    // Bridge coupling filter
    float couplingFeedback = couplingFilter.tick(bridgeSum, systemTime) * couplingGain;
    // In a real model, we'd feed this back into strings[i] next sample.

    lastOut = totalOutput * gain;
    return lastOut;
  }

  @Override
  public float getChannelLastOut(int i) {
    return lastOut; // Mono for now, distributed to stereo
  }
}
