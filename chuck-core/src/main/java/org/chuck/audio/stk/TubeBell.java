package org.chuck.audio.stk;

import org.chuck.audio.stk.fm.FmInstrument;
import org.chuck.audio.stk.fm.FmWaveLoop;
import org.chuck.audio.stk.fm.Rawwaves;

/**
 * TubeBell — STK's 4-operator FM tubular bell, ported verbatim from ugen_stk.cpp. Uses the shared
 * Repairathon "compatible" FM tick. Replaces the earlier approximation.
 */
public class TubeBell extends FmInstrument {

  public TubeBell() {
    super();
    init();
  }

  public TubeBell(float sampleRate) {
    super(sampleRate);
    init();
  }

  private void init() {
    waves[0] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[1] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[2] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[3] = new FmWaveLoop(Rawwaves.FWAVBLNK, sampleRate);

    setRatio(0, 1.0 * 0.995);
    setRatio(1, 1.414 * 0.995);
    setRatio(2, 1.0 * 1.005);
    setRatio(3, 1.414 * 1.000);

    gains[0] = FM_GAINS[94];
    gains[1] = FM_GAINS[76];
    gains[2] = FM_GAINS[99];
    gains[3] = FM_GAINS[71];
    baseGains[0] = gains[0];
    baseGains[1] = gains[1];
    baseGains[2] = gains[2];
    baseGains[3] = gains[3];

    adsr[0].setAllTimes(0.005, 4.0, 0.0, 0.04);
    adsr[1].setAllTimes(0.005, 4.0, 0.0, 0.04);
    adsr[2].setAllTimes(0.001, 2.0, 0.0, 0.04);
    adsr[3].setAllTimes(0.004, 4.0, 0.0, 0.04);

    twozero.setGain(0.5);
    vibrato.setFrequency(2.0);
    setFrequency(baseFrequency);
  }

  @Override
  protected float compute(float input, long systemTime) {
    return tickCompatible();
  }
}
