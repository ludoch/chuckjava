package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.osc.SinOsc;
import org.chuck.audio.util.Adsr;

/** A simple Fender Rhodes model using 2-operator FM synthesis. */
public class Rhodey extends ChuckUGen {
  private final SinOsc carrier;
  private final SinOsc modulator;
  private final Adsr carrierEnv;
  private final Adsr modulatorEnv;
  private double baseFreq = 440.0;

  @SuppressWarnings("unused")
  private float modIndex = 0.5f;

  @SuppressWarnings("unused")
  private final float sampleRate;

  public Rhodey(float sampleRate) {
    this.sampleRate = sampleRate;
    // Disable auto-registration for internal parts
    this.carrier = new SinOsc(sampleRate, false);
    this.modulator = new SinOsc(sampleRate, false);
    this.carrierEnv = new Adsr(sampleRate, false);
    this.modulatorEnv = new Adsr(sampleRate, false);

    // Rhodey ratio is 1:1 carrier:modulator for classic bell sound
    carrier.setFreq(440.0);
    modulator.setFreq(440.0);

    // Configure envelopes
    carrierEnv.set(0.001f, 1.5f, 0.0f, 0.05f);
    modulatorEnv.set(0.001f, 0.5f, 0.0f, 0.05f);
  }

  public void setFreq(double freq) {
    this.baseFreq = freq;
    carrier.setFreq(freq);
    modulator.setFreq(freq); // 1:1 ratio
  }

  public void noteOn(float velocity) {
    carrierEnv.keyOn();
    modulatorEnv.keyOn();
    // Mod index scales depth
    modIndex = velocity;
  }

  public void noteOff(float velocity) {
    carrierEnv.keyOff();
    modulatorEnv.keyOff();
  }

  @Override
  protected float compute(float input, long systemTime) {
    float mEnv = modulatorEnv.tick(systemTime);

    // Modulator drives the phase of carrier (simple FM)
    float modOut = modulator.tick(systemTime) * modIndex * mEnv;

    // Manual FM: offset carrier frequency by modulator output
    carrier.setFreq(baseFreq + (modOut * 500.0));
    float carOut = carrier.tick(systemTime);

    float cEnv = carrierEnv.tick(systemTime);

    // Result is carrier enveloped by its own ADSR
    lastOut = carOut * cEnv * gain;
    return lastOut;
  }
}
