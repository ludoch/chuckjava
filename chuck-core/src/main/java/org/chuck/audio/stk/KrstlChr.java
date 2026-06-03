package org.chuck.audio.stk;

import org.chuck.audio.stk.fm.FmInstrument;
import org.chuck.audio.stk.fm.FmWaveLoop;
import org.chuck.audio.stk.fm.Rawwaves;
import org.chuck.core.doc;

/**
 * KrstlChr — STK/ChucK "crystal choir", a Repairathon-2021 4-operator FM preset on the shared
 * {@link FmInstrument} engine (NOT an additive approximation). Operators use the {@code sineblnk},
 * {@code snglpeak}, {@code sinewave}, {@code snglpeak} waves in a cascade where op3 (fed back
 * through the TwoZero) phase-modulates op2, and op0 is summed in parallel. Ported verbatim from
 * {@code KrstlChr::KrstlChr} / {@code KrstlChr::tick}.
 */
@doc("FM crystal-choir voice based on STK. Ported to Java 25.")
public class KrstlChr extends FmInstrument {

  public KrstlChr() {
    super();
    init();
  }

  public KrstlChr(float sampleRate) {
    super(sampleRate);
    init();
  }

  private void init() {
    waves[0] = new FmWaveLoop(Rawwaves.SINEBLNK, sampleRate);
    waves[1] = new FmWaveLoop(Rawwaves.SNGLPEAK, sampleRate);
    waves[2] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[3] = new FmWaveLoop(Rawwaves.SNGLPEAK, sampleRate);

    setRatio(0, 1.00);
    setRatio(1, 0.99);
    setRatio(2, 1.01);
    setRatio(3, 8.63);

    gains[0] = FM_GAINS[99];
    gains[1] = FM_GAINS[99];
    gains[2] = FM_GAINS[89];
    gains[3] = FM_GAINS[77];
    baseGains[0] = gains[0];
    baseGains[1] = gains[1];
    baseGains[2] = gains[2];
    baseGains[3] = gains[3];

    adsr[0].setAllTimes(FM_ATT_TIMES[14], FM_ATT_TIMES[8], 1.0, FM_ATT_TIMES[5]);
    adsr[1].setAllTimes(FM_ATT_TIMES[11], FM_ATT_TIMES[8], 1.0, FM_ATT_TIMES[7]);
    adsr[2].setAllTimes(FM_ATT_TIMES[15], FM_ATT_TIMES[7], 1.0, FM_ATT_TIMES[7]);
    adsr[3].setAllTimes(FM_ATT_TIMES[31], FM_ATT_TIMES[0], 1.0, FM_ATT_TIMES[4]);

    twozero.setGain(2.0); // Op4 feedback
    vibrato.setFrequency(3.5);
    modDepth = 0.02;

    setFrequency(baseFrequency);
  }

  @Override
  protected float compute(float input, long systemTime) {
    double temp2 = vibrato.tick() * modDepth * 0.2; // saved for AM
    double temp = vibrato.tick() * modDepth * 0.2; // NOTE: STK advances vibrato twice per sample
    for (int i = 0; i < 4; i++) {
      if (ratios[i] > 0.0) waves[i].setFrequency(baseFrequency * (1.0 + temp) * ratios[i]);
    }

    waves[3].addPhaseOffset(twozero.lastOut());
    twozero.tick(temp);
    temp = (1.0 + opAMs[3] * temp2) * gains[3] * adsr[3].tick() * waves[3].tick();

    waves[2].addPhaseOffset(temp);
    temp =
        (1.0 + opAMs[2] * temp2 - (control2 * 0.5)) * gains[2] * adsr[2].tick() * waves[2].tick();

    temp += (1.0 + opAMs[1] * temp2) * control2 * 0.5 * gains[1] * adsr[1].tick() * waves[1].tick();
    temp = temp * control1;

    temp += (1.0 + opAMs[0] * temp2) * gains[0] * adsr[0].tick() * waves[0].tick();

    lastOut = (float) (temp * 0.5) * gain;
    return lastOut;
  }
}
