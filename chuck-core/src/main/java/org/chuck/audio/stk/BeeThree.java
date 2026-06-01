package org.chuck.audio.stk;

import org.chuck.audio.stk.fm.FmInstrument;
import org.chuck.audio.stk.fm.FmWaveLoop;
import org.chuck.audio.stk.fm.Rawwaves;

/**
 * BeeThree — STK's Hammond-B3-style 4-operator FM organ, ported verbatim from ChucK's ugen_stk.cpp
 * (BeeThree::BeeThree / BeeThree::tick). Operators 0-2 are sine waves, operator 3 is the {@code
 * fwavblnk} table fed back through a TwoZero for phase modulation. Replaces the earlier 3-SinOsc
 * additive approximation; now sample-accurate against native ChucK.
 */
public class BeeThree extends FmInstrument {

  public BeeThree() {
    super();
    init();
  }

  public BeeThree(float sampleRate) {
    super(sampleRate);
    init();
  }

  private void init() {
    waves[0] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[1] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[2] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[3] = new FmWaveLoop(Rawwaves.FWAVBLNK, sampleRate);

    setRatio(0, 0.999);
    setRatio(1, 1.997);
    setRatio(2, 3.006);
    setRatio(3, 6.009);

    gains[0] = FM_GAINS[95];
    gains[1] = FM_GAINS[95];
    gains[2] = FM_GAINS[99];
    gains[3] = FM_GAINS[95];
    baseGains[0] = gains[0];
    baseGains[1] = gains[1];
    baseGains[2] = gains[2];
    baseGains[3] = gains[3];

    adsr[0].setAllTimes(0.005, 0.003, 1.0, 0.01);
    adsr[1].setAllTimes(0.005, 0.003, 1.0, 0.01);
    adsr[2].setAllTimes(0.005, 0.003, 1.0, 0.01);
    adsr[3].setAllTimes(0.005, 0.001, 0.4, 0.03);

    twozero.setGain(0.1);

    // Initialize operator frequencies at the default base frequency.
    setFrequency(baseFrequency);
  }

  @Override
  protected float compute(float input, long systemTime) {
    double temp2 = vibrato.tick();
    double temp = temp2 * modDepth * 0.2;
    for (int i = 0; i < 4; i++) {
      if (ratios[i] > 0.0) waves[i].setFrequency(baseFrequency * (1.0 + temp) * ratios[i]);
    }
    waves[3].addPhaseOffset(twozero.lastOut());
    temp = (1.0 + opAMs[3] * temp2) * control1 * 2.0 * gains[3] * adsr[3].tick() * waves[3].tick();
    twozero.tick(temp);
    temp += (1.0 + opAMs[2] * temp2) * control2 * 2.0 * gains[2] * adsr[2].tick() * waves[2].tick();
    temp += (1.0 + opAMs[1] * temp2) * gains[1] * adsr[1].tick() * waves[1].tick();
    temp += (1.0 + opAMs[0] * temp2) * gains[0] * adsr[0].tick() * waves[0].tick();

    return (float) (temp * 0.125) * gain;
  }
}
