package org.chuck.audio.stk;

import org.chuck.audio.stk.fm.FmInstrument;
import org.chuck.audio.stk.fm.FmWaveLoop;
import org.chuck.audio.stk.fm.Rawwaves;

/**
 * Wurley — STK's 4-operator FM Wurlitzer electric piano, ported verbatim from ugen_stk.cpp.
 * Operators 2 and 3 use fixed resonant frequencies (ratios stored as absolute Hz). Uses the shared
 * Repairathon "compatible" FM tick.
 */
public class Wurley extends FmInstrument {

  public Wurley() {
    super();
    init();
  }

  public Wurley(float sampleRate) {
    super(sampleRate);
    init();
  }

  private void init() {
    waves[0] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[1] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[2] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[3] = new FmWaveLoop(Rawwaves.FWAVBLNK, sampleRate);

    setRatio(0, 1.0);
    setRatio(1, 4.0);
    setRatio(2, -510.0);
    setRatio(3, -510.0);

    gains[0] = FM_GAINS[99];
    gains[1] = FM_GAINS[82];
    gains[2] = FM_GAINS[92];
    gains[3] = FM_GAINS[68];
    baseGains[0] = gains[0];
    baseGains[1] = gains[1];
    baseGains[2] = gains[2];
    baseGains[3] = gains[3];

    adsr[0].setAllTimes(0.001, 1.50, 0.0, 0.04);
    adsr[1].setAllTimes(0.001, 1.50, 0.0, 0.04);
    adsr[2].setAllTimes(0.001, 0.25, 0.0, 0.04);
    adsr[3].setAllTimes(0.001, 0.15, 0.0, 0.04);

    twozero.setGain(2.0);
    vibrato.setFrequency(8.0);
    setFrequency(baseFrequency);
  }

  /** Wurley::setFrequency — ops 2 and 3 are fixed resonances (ratios used as absolute Hz). */
  @Override
  public void setFrequency(double frequency) {
    baseFrequency = frequency;
    waves[0].setFrequency(baseFrequency * ratios[0]);
    waves[1].setFrequency(baseFrequency * ratios[1]);
    waves[2].setFrequency(ratios[2]);
    waves[3].setFrequency(ratios[3]);
  }

  @Override
  protected float compute(float input, long systemTime) {
    return tickCompatible();
  }
}
