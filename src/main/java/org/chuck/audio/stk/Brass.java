package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.filter.BiQuad;
import org.chuck.audio.filter.OnePole;
import org.chuck.audio.fx.DelayL;
import org.chuck.audio.util.Adsr;
import org.chuck.core.doc;

/**
 * Brass: STK lip-reed physical model. Based on digital waveguide theory and mass-spring-damper lip
 * simulation.
 */
@doc("Brass physical model based on STK. Models lip-reed interaction.")
public class Brass extends ChuckUGen {
  private final DelayL delayLine;
  private final BiQuad lipFilter;
  private final OnePole bellFilter;
  private final Adsr adsr;

  private double lipTarget = 440.0;
  private float pressure = 0.0f;
  private final float sampleRate;

  public Brass(float sampleRate) {
    this.sampleRate = sampleRate;
    int maxDelay = (int) (sampleRate / 20.0);
    delayLine = new DelayL(maxDelay, sampleRate);

    lipFilter = new BiQuad(sampleRate);
    lipFilter.gain(0.03); // STK default gain

    bellFilter = new OnePole(sampleRate);
    bellFilter.setPole(0.7f);

    adsr = new Adsr(sampleRate);
    adsr.set(0.05f, 0.05f, 0.9f, 0.1f);

    setFreq(440.0);
  }

  @doc("Set the instrument frequency in Hz.")
  public void freq(double f) {
    // STK tuning: period * 2 + fudge factor
    double delay = (sampleRate / f) + 3.0;
    delayLine.delay(delay);

    // Set initial lip resonance to match frequency
    this.lipTarget = f;
    lipFilter.radius(0.997);
    lipFilter.freq(f);
  }

  public void setFreq(double f) {
    freq(f);
  }

  @doc("Set lip tension/resonance frequency directly.")
  public void lip(double f) {
    this.lipTarget = f;
    lipFilter.freq(f);
  }

  @doc("Start a note with given volume/velocity.")
  public void noteOn(float velocity) {
    pressure = 0.1f + velocity * 0.9f;
    adsr.keyOn();
  }

  @doc("Stop the note.")
  public void noteOff(float velocity) {
    adsr.keyOff();
  }

  @Override
  protected float compute(float input, long systemTime) {
    float env = adsr.tick(systemTime);
    float breath = pressure * env;

    // Waveguide feedback loop
    float boreRes = -delayLine.getLastOut(); // inversion at bell
    float filteredBore = bellFilter.tick(boreRes, systemTime);

    // Lip resonator interaction
    float jawRes = breath - filteredBore;
    float lipOutput = lipFilter.tick(jawRes, systemTime);

    // Simple non-linear saturation for the lip valve
    if (lipOutput > 1.0f) lipOutput = 1.0f;
    if (lipOutput < -1.0f) lipOutput = -1.0f;

    float out = delayLine.tick(breath + lipOutput, systemTime);

    lastOut = out * gain;
    return lastOut;
  }
}
