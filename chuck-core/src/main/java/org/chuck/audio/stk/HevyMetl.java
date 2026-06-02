package org.chuck.audio.stk;

import org.chuck.audio.stk.fm.FmInstrument;
import org.chuck.audio.stk.fm.FmWaveLoop;
import org.chuck.audio.stk.fm.Rawwaves;

/**
 * HevyMetl — STK's 4-operator FM "heavy metal" lead, ported verbatim from ugen_stk.cpp
 * (HevyMetl::tick). Cascade FM: op3→op2, op4(feedback)→ blended with op2, → op1. Replaces the
 * earlier approximation.
 */
public class HevyMetl extends FmInstrument {

  public HevyMetl() {
    super();
    init();
  }

  public HevyMetl(float sampleRate) {
    super(sampleRate);
    init();
  }

  private void init() {
    waves[0] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[1] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[2] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[3] = new FmWaveLoop(Rawwaves.FWAVBLNK, sampleRate);

    setRatio(0, 1.0 * 1.000);
    setRatio(1, 4.0 * 0.999);
    setRatio(2, 3.0 * 1.001);
    setRatio(3, 0.5 * 1.002);

    gains[0] = FM_GAINS[92];
    gains[1] = FM_GAINS[76];
    gains[2] = FM_GAINS[91];
    gains[3] = FM_GAINS[68];
    // Note: STK sets all four baseGains to gains[0] here (verbatim).
    baseGains[0] = gains[0];
    baseGains[1] = gains[0];
    baseGains[2] = gains[0];
    baseGains[3] = gains[0];

    adsr[0].setAllTimes(0.001, 0.001, 1.0, 0.01);
    adsr[1].setAllTimes(0.001, 0.010, 1.0, 0.50);
    adsr[2].setAllTimes(0.010, 0.005, 1.0, 0.20);
    adsr[3].setAllTimes(0.030, 0.010, 0.2, 0.20);

    twozero.setGain(2.0);
    vibrato.setFrequency(5.5);
    modDepth = 0.0;
    setFrequency(baseFrequency);
  }

  /** {@code m.lfoSpeed(v)} */
  public void lfoSpeed(float v) {
    setModulationSpeed(v);
  }

  /** {@code m.lfoDepth(v)} */
  public void lfoDepth(float v) {
    setModulationDepth(v);
  }

  /** {@code m.freq()} getter */
  public double freq() {
    return baseFrequency;
  }

  @Override
  protected float compute(float input, long systemTime) {
    double temp2 = vibrato.tick();
    double temp = temp2 * modDepth * 0.2;
    for (int i = 0; i < 4; i++) {
      if (ratios[i] > 0.0) waves[i].setFrequency(baseFrequency * (1.0 + temp) * ratios[i]);
    }

    temp = (1.0 + opAMs[2] * temp2) * gains[2] * adsr[2].tick() * waves[2].tick(); // Op3
    waves[1].addPhaseOffset(temp);

    waves[3].addPhaseOffset(twozero.lastOut()); // Op4
    temp =
        (1.0 + opAMs[3] * temp2 - (control2 * 0.5)) * gains[3] * adsr[3].tick() * waves[3].tick();
    twozero.tick(temp);

    temp += (1.0 + opAMs[1] * temp2) * control2 * 0.5 * gains[1] * adsr[1].tick() * waves[1].tick();
    temp = temp * control1;

    waves[0].addPhaseOffset(temp);
    temp = (1.0 + opAMs[0] * temp2) * gains[0] * adsr[0].tick() * waves[0].tick(); // Op1

    return (float) (temp * 0.5) * gain;
  }
}
