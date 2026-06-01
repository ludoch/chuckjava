package org.chuck.audio.stk;

import org.chuck.audio.stk.fm.FmInstrument;
import org.chuck.audio.stk.fm.FmWaveLoop;
import org.chuck.audio.stk.fm.Rawwaves;

/**
 * Rhodey — STK's 4-operator FM Rhodes electric piano, ported verbatim from ugen_stk.cpp. The base
 * frequency is doubled internally (Rhodey::setFrequency). Uses the shared Repairathon "compatible"
 * FM tick.
 */
public class Rhodey extends FmInstrument {

  public Rhodey() {
    super();
    init();
  }

  public Rhodey(float sampleRate) {
    super(sampleRate);
    init();
  }

  private void init() {
    waves[0] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[1] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[2] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[3] = new FmWaveLoop(Rawwaves.FWAVBLNK, sampleRate);

    setRatio(0, 1.0);
    setRatio(1, 0.5);
    setRatio(2, 1.0);
    setRatio(3, 15.0);

    gains[0] = FM_GAINS[99];
    gains[1] = FM_GAINS[90];
    gains[2] = FM_GAINS[99];
    gains[3] = FM_GAINS[67];
    baseGains[0] = gains[0];
    baseGains[1] = gains[1];
    baseGains[2] = gains[2];
    baseGains[3] = gains[3];

    adsr[0].setAllTimes(0.001, 1.50, 0.0, 0.04);
    adsr[1].setAllTimes(0.001, 1.50, 0.0, 0.04);
    adsr[2].setAllTimes(0.001, 1.00, 0.0, 0.04);
    adsr[3].setAllTimes(0.001, 0.25, 0.0, 0.04);

    twozero.setGain(1.0);
    setFrequency(220.0); // matches default after the *2 below (baseFrequency 440)
  }

  /** Rhodey::setFrequency — internal base frequency is twice the requested pitch. */
  @Override
  public void setFrequency(double frequency) {
    baseFrequency = frequency * 2.0;
    for (int i = 0; i < N_OPERATORS; i++) waves[i].setFrequency(baseFrequency * ratios[i]);
  }

  @Override
  protected float compute(float input, long systemTime) {
    return tickCompatible();
  }
}
