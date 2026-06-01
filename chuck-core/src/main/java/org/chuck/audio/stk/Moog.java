package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.stk.fm.FmWaveLoop;
import org.chuck.audio.stk.fm.Rawwaves;
import org.chuck.audio.stk.fm.StkAdsr;
import org.chuck.audio.stk.util.StkFormSwep;
import org.chuck.audio.stk.util.StkOnePole;
import org.chuck.audio.stk.util.StkWvIn;
import org.chuck.audio.stk.util.WaveData;

/**
 * Moog — STK's sampling synthesis voice (Sampler + Moog), ported verbatim from ugen_stk.cpp. An
 * attack transient (special:mandpluk) plus a looped wave (special:impuls20) are summed, passed
 * through a one-pole filter and ADSR, then through two FormSwep resonant filters that sweep toward
 * the note frequency. Replaces the earlier SinOsc+LPF approximation.
 */
public class Moog extends ChuckUGen {
  private final double sampleRate;

  // ── Sampler state ─────────────────────────────────────────────────────────
  private final StkAdsr adsr;
  private final StkOnePole filter = new StkOnePole();
  private double baseFrequency = 440.0;
  private double attackGain = 0.25;
  private double loopGain = 0.25;

  private final StkWvIn attack; // attacks[0]
  private final FmWaveLoop loop; // loops[0]
  private final FmWaveLoop vibrato; // loops[1]

  // ── Moog state ────────────────────────────────────────────────────────────
  private final StkFormSwep filter0;
  private final StkFormSwep filter1;
  private double filterQ = 0.85;
  private double filterRate = 0.0001;
  private double filterStartFreq = 2000.0;
  private double modDepth = 0.0;

  public Moog(float sampleRate) {
    this.sampleRate = sampleRate;
    adsr = new StkAdsr(sampleRate);
    attack = new StkWvIn(WaveData.MANDPLUK, sampleRate);
    loop = new FmWaveLoop(WaveData.IMPULS20, sampleRate);
    vibrato = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    vibrato.setFrequency(6.122);

    filter0 = new StkFormSwep(sampleRate);
    filter1 = new StkFormSwep(sampleRate);
    filter0.setTargets(0.0, 0.7);
    filter1.setTargets(0.0, 0.7);

    adsr.setAllTimes(0.001, 1.5, 0.6, 0.250);
  }

  public void setFrequency(double frequency) {
    baseFrequency = (frequency <= 0.0) ? 220.0 : frequency;
    double rate = attack.getSize() * 0.01 * baseFrequency / sampleRate;
    attack.setRate(rate);
    loop.setFrequency(baseFrequency);
  }

  public void setFreq(double f) {
    setFrequency(f);
  }

  private void keyOn() {
    adsr.keyOn();
    attack.reset();
  }

  /** {@code amp => m.noteOn} — STK Moog::noteOn(amplitude) uses the current base frequency. */
  public void noteOn(float amplitude) {
    setFrequency(baseFrequency);
    keyOn();
    attackGain = amplitude * 0.5;
    loopGain = amplitude;

    double temp = filterQ + 0.05;
    filter0.setStates(filterStartFreq, temp);
    filter1.setStates(filterStartFreq, temp);

    temp = filterQ + 0.099;
    filter0.setTargets(baseFrequency, temp);
    filter1.setTargets(baseFrequency, temp);

    filter0.setSweepRate(filterRate * 22050.0 / sampleRate);
    filter1.setSweepRate(filterRate * 22050.0 / sampleRate);
  }

  public void noteOff(float amplitude) {
    adsr.keyOff();
  }

  public void filterQ(float q) {
    filterQ = 0.80 + 0.1 * q;
  }

  public void filterSweep(float s) {
    filterRate = s * 0.0002;
  }

  public void lfoSpeed(float v) {
    vibrato.setFrequency(v);
  }

  public void lfoDepth(float v) {
    modDepth = v * 0.5;
  }

  /** Sampler::tick — attack transient + looped wave, one-pole filtered, ADSR-scaled. */
  private double samplerTick() {
    double out = attackGain * attack.tick();
    out += loopGain * loop.tick();
    out = filter.tick(out);
    out *= adsr.tick();
    return out;
  }

  @Override
  protected float compute(float input, long systemTime) {
    if (modDepth != 0.0) {
      double temp = vibrato.tick() * modDepth;
      loop.setFrequency(baseFrequency * (1.0 + temp));
    }
    double temp = samplerTick();
    temp = filter0.tick(temp);
    temp = filter1.tick(temp);
    return (float) (temp * 3.0) * gain;
  }
}
