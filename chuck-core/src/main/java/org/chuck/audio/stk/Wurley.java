package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.osc.SinOsc;
import org.chuck.audio.util.Adsr;

/**
 * Wurley — Wurlitzer electric piano using 2-operator FM synthesis. More nasal and honky than
 * Rhodey, with a higher modulator ratio.
 */
public class Wurley extends ChuckUGen {
  private final SinOsc carrier;
  private final SinOsc modulator;
  private final Adsr carrierEnv;
  private final Adsr modulatorEnv;
  private double baseFreq = 440.0;
  private float modIndex = 1.0f;

  @SuppressWarnings("unused")
  private final float sampleRate;

  public Wurley(float sampleRate) {
    this.sampleRate = sampleRate;
    this.carrier = new SinOsc(sampleRate);
    this.modulator = new SinOsc(sampleRate);
    this.carrierEnv = new Adsr(sampleRate);
    this.modulatorEnv = new Adsr(sampleRate);

    modulator.chuckTo(carrier);
    carrier.setSync(2); // FM mode

    // Wurlitzer: characteristic nasal tone
    carrierEnv.set(0.002f, 1.2f, 0.0f, 0.08f);
    modulatorEnv.set(0.001f, 0.3f, 0.0f, 0.05f);
  }

  public void setFreq(double freq) {
    this.baseFreq = freq;
    carrier.setFreq(freq);
    modulator.setFreq(freq * 4.0); // Higher ratio for nasal quality
  }

  public void noteOn(float velocity) {
    carrierEnv.keyOn();
    modulatorEnv.keyOn();
    modIndex = (float) (baseFreq * velocity * 1.0);
  }

  public void noteOff(float velocity) {
    carrierEnv.keyOff();
    modulatorEnv.keyOff();
  }

  @Override
  protected float compute(float input, long systemTime) {
    @SuppressWarnings("unused")
    float mEnv = modulatorEnv.tick(systemTime);
    float carOut = carrier.tick(systemTime);
    float modOut = modulator.tick(systemTime);
    float cEnv = carrierEnv.tick(systemTime);
    // Wurley has more modulator contribution than Rhodey
    float out = (carOut + modOut * 0.3f) * cEnv * gain;
    return out;
  }
}
