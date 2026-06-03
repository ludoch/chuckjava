package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.filter.OnePole;
import org.chuck.audio.filter.PoleZero;
import org.chuck.audio.fx.DelayL;
import org.chuck.audio.osc.Noise;
import org.chuck.audio.osc.SinOsc;
import org.chuck.audio.util.Adsr;
import org.chuck.audio.util.JetTabl;
import org.chuck.core.doc;

/**
 * Flute: STK jet-reflection waveguide flute. Faithful port of the STK/ChucK {@code Flute} class:
 * separate bore and jet delay lines, a OnePole reflection filter (gain -1) with a DC-blocking
 * {@link PoleZero}, the {@link JetTabl} non-linear jet function, and additive breath noise/vibrato.
 */
@doc("Flute physical model based on STK. Ported to Java 25.")
public class Flute extends ChuckUGen {
  private final DelayL boreDelay;
  private final DelayL jetDelay;
  private final JetTabl jetTable;
  private final OnePole filter;
  private final PoleZero dcBlock;
  private final Noise noise;
  private final SinOsc vibrato;
  private final Adsr adsr;

  private final float sampleRate;
  private final int length;

  private float noiseGain = 0.15f; // Breath pressure random component.
  private float vibratoGain = 0.05f; // Breath periodic vibrato component.
  private float endReflection = 0.5f;
  private float jetReflection = 0.5f;
  private float jetRatio = 0.32f;
  private float maxPressure = 0.0f;
  private float outputGain = 1.0f;
  private float lastFrequency = 220.0f;

  public Flute() {
    this(org.chuck.core.ChuckVM.CURRENT_VM.get().getSampleRate());
  }

  public Flute(float sampleRate) {
    this(40.0f, sampleRate);
  }

  public Flute(float lowestFrequency, float sampleRate) {
    this.sampleRate = sampleRate;
    int full = (int) (sampleRate / lowestFrequency + 1);
    this.boreDelay = new DelayL(full, sampleRate, false);
    this.length = full >> 1; // jet delay buffer is half the bore length
    this.jetDelay = new DelayL(length, sampleRate, false);
    this.jetTable = new JetTabl(false);
    this.filter = new OnePole(sampleRate, false);
    this.dcBlock = new PoleZero(false);
    this.dcBlock.setBlockZero(0.99);
    this.noise = new Noise(false);
    this.vibrato = new SinOsc(sampleRate, false);
    this.vibrato.setFreq(5.925);
    this.adsr = new Adsr(sampleRate, false);

    // OnePole reflection filter: pole = 0.7 - 0.1*22050/sr, gain = -1 (folded into b0).
    float pole = (float) (0.7 - 0.1 * 22050.0 / sampleRate);
    filter.setPole(pole);
    filter.setB0(-(1.0f - pole)); // apply STK setGain(-1.0): negate the (1-pole) numerator

    adsr.set(0.005f, 0.01f, 0.8f, 0.010f); // attack/decay/release in seconds, sustain 0.8

    setFreq(220.0);
  }

  @doc("Set the flute frequency in Hz.")
  public void freq(double frequency) {
    setFreq(frequency);
  }

  public void setFreq(double frequency) {
    lastFrequency = (float) (frequency <= 0.0 ? 220.0 : frequency);
    // STK overblows by 2/3.
    float f = lastFrequency * 0.66666f;
    double delay = sampleRate / f - 2.0;
    if (delay <= 0.0) delay = 0.3;
    else if (delay > length) delay = length; // STK clamps to the (halved) bore length
    boreDelay.setDelay(delay);
    jetDelay.setDelay(delay * jetRatio);
  }

  @doc("Start a note with given volume/velocity.")
  public void noteOn(float velocity) {
    // STK noteOn -> startBlowing(1.1 + amp*0.20, amp*0.02); outputGain = amp + 0.001.
    float amplitude = 1.1f + velocity * 0.20f;
    float rate = velocity * 0.02f;
    maxPressure = amplitude / 0.8f;
    // STK setAttackRate(rate): attackInc per sample == rate, i.e. attack time = 1/rate samples.
    adsr.attackTime(rate > 0 ? 1.0 / rate : 1.0);
    adsr.keyOn();
    outputGain = velocity + 0.001f;
  }

  @doc("Stop the note.")
  public void noteOff(float velocity) {
    // STK noteOff -> stopBlowing(amp*0.02): set release rate, then key off.
    float rate = velocity * 0.02f;
    adsr.releaseTime(rate > 0 ? 1.0 / rate : 1.0);
    adsr.keyOff();
  }

  @Override
  protected float compute(float input, long systemTime) {
    // Breath pressure: envelope, then multiplicative noise and vibrato.
    float breathPressure = maxPressure * adsr.tick(systemTime);
    breathPressure += breathPressure * noiseGain * noise.tick(systemTime);
    breathPressure += breathPressure * vibratoGain * vibrato.tick(systemTime);

    // Reflection: OnePole-filtered bore output, DC-blocked.
    float temp = filter.tick(boreDelay.getLastOut(), systemTime);
    temp = dcBlock.tick(temp, systemTime);

    float pressureDiff = breathPressure - (jetReflection * temp);
    pressureDiff = jetDelay.tick(pressureDiff, systemTime);
    pressureDiff = jetTable.tick(pressureDiff) + (endReflection * temp);

    float out = 0.3f * boreDelay.tick(pressureDiff, systemTime);
    lastOut = out * outputGain;
    return lastOut;
  }
}
