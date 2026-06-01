package org.chuck.audio.stk;

import org.chuck.audio.stk.fm.FmInstrument;
import org.chuck.audio.stk.fm.FmWaveLoop;
import org.chuck.audio.stk.fm.Rawwaves;

/**
 * PercFlut — STK's 4-operator FM percussive flute, ported verbatim from ugen_stk.cpp
 * (PercFlut::tick). Operator frequencies are updated each sample from baseFrequency, so
 * setFrequency only stores the base pitch. Replaces the earlier approximation.
 */
public class PercFlut extends FmInstrument {

  public PercFlut() {
    super();
    init();
  }

  public PercFlut(float sampleRate) {
    super(sampleRate);
    init();
  }

  private void init() {
    waves[0] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[1] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[2] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[3] = new FmWaveLoop(Rawwaves.FWAVBLNK, sampleRate);

    setRatio(0, 1.50 * 1.000);
    setRatio(1, 3.00 * 0.995);
    setRatio(2, 2.99 * 1.005);
    setRatio(3, 6.00 * 0.997);

    gains[0] = FM_GAINS[99];
    gains[1] = FM_GAINS[71];
    gains[2] = FM_GAINS[93];
    gains[3] = FM_GAINS[85];
    baseGains[0] = gains[0];
    baseGains[1] = gains[1];
    baseGains[2] = gains[2];
    baseGains[3] = gains[3];

    adsr[0].setAllTimes(0.05, 0.05, FM_SUS_LEVELS[14], 0.05);
    adsr[1].setAllTimes(0.02, 0.50, FM_SUS_LEVELS[13], 0.5);
    adsr[2].setAllTimes(0.02, 0.30, FM_SUS_LEVELS[11], 0.05);
    adsr[3].setAllTimes(0.02, 0.05, FM_SUS_LEVELS[13], 0.01);

    twozero.setGain(0.0);
    modDepth = 0.005;
  }

  /** PercFlut::setFrequency — store base pitch only; tick re-sets operator frequencies. */
  @Override
  public void setFrequency(double frequency) {
    baseFrequency = frequency;
  }

  /** PercFlut::noteOn scales operator gains by 0.5 (verbatim). */
  @Override
  public void noteOn(float amplitude) {
    for (int i = 0; i < N_OPERATORS; i++) gains[i] = amplitude * baseGains[i] * 0.5;
    keyOn();
  }

  @Override
  protected float compute(float input, long systemTime) {
    double temp2 = vibrato.tick();
    double temp = temp2 * modDepth * 0.2;
    for (int i = 0; i < 4; i++) {
      if (ratios[i] > 0.0) waves[i].setFrequency(baseFrequency * (1.0 + temp) * ratios[i]);
    }

    waves[3].addPhaseOffset(twozero.lastOut());
    temp = (1.0 + opAMs[3] * temp2) * gains[3] * adsr[3].tick() * waves[3].tick();

    twozero.tick(temp);
    waves[2].addPhaseOffset(temp);
    temp = (1.0 + opAMs[2] * temp2 - (control2 * 0.5)) * gains[2] * adsr[2].tick() * waves[2].tick();

    temp += (1.0 + opAMs[1] * temp2) * control2 * 0.5 * gains[1] * adsr[1].tick() * waves[1].tick();
    temp = temp * control1;

    waves[0].addPhaseOffset(temp);
    temp = (1.0 + opAMs[0] * temp2) * gains[0] * adsr[0].tick() * waves[0].tick();

    return (float) (temp * 0.5) * gain;
  }
}
