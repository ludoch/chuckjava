package org.chuck.audio.stk;

import org.chuck.audio.stk.fm.FmInstrument;
import org.chuck.audio.stk.fm.FmWaveLoop;
import org.chuck.audio.stk.fm.Phonemes;
import org.chuck.audio.stk.fm.Rawwaves;

/**
 * FMVoices — STK's singing FM voice (TX81Z algorithm 6: three carriers + one common modulator),
 * ported verbatim from ugen_stk.cpp. The selected vowel's formant frequencies set the carrier
 * ratios; per-carrier "tilt" gains shape the spectrum. Replaces the earlier approximation.
 */
public class FMVoices extends FmInstrument {
  private int currentVowel = 0;
  private final double[] tilt = {1.0, 0.5, 0.2};
  private final double[] mods = {1.0, 1.1, 1.1};

  public FMVoices() {
    super();
    init();
  }

  public FMVoices(float sampleRate) {
    super(sampleRate);
    init();
  }

  private void init() {
    waves[0] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[1] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[2] = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    waves[3] = new FmWaveLoop(Rawwaves.FWAVBLNK, sampleRate);

    setRatio(0, 2.0);
    setRatio(1, 4.0);
    setRatio(2, 12.0);
    setRatio(3, 1.0);

    gains[3] = FM_GAINS[80];
    // gains[0..2] remain 1.0 (FM base default), matching STK.
    baseGains[0] = gains[0];
    baseGains[1] = gains[1];
    baseGains[2] = gains[2];
    baseGains[3] = gains[3];

    adsr[0].setAllTimes(0.05, 0.05, FM_SUS_LEVELS[15], 0.05);
    adsr[1].setAllTimes(0.05, 0.05, FM_SUS_LEVELS[15], 0.05);
    adsr[2].setAllTimes(0.05, 0.05, FM_SUS_LEVELS[15], 0.05);
    adsr[3].setAllTimes(0.01, 0.01, FM_SUS_LEVELS[15], 0.5);

    twozero.setGain(0.0);
    modDepth = 0.005;
    baseFrequency = 110.0;
    setFrequency(110.0);
  }

  /** {@code m.vibratoDepth} */
  public void setVibratoDepth(double depth) {
    setModulationDepth(depth);
  }

  /**
   * FMVoices::setFrequency — derive carrier ratios from the current vowel's formant frequencies.
   */
  @Override
  public void setFrequency(double frequency) {
    int i = 0;
    double temp2 = 0.0;
    if (currentVowel < 32) {
      i = currentVowel;
      temp2 = 0.9;
    } else if (currentVowel < 64) {
      i = currentVowel - 32;
      temp2 = 1.0;
    } else if (currentVowel < 96) {
      i = currentVowel - 64;
      temp2 = 1.1;
    } else if (currentVowel <= 128) {
      i = currentVowel - 96;
      temp2 = 1.2;
    }

    baseFrequency = frequency;
    setRatio(0, (int) ((temp2 * Phonemes.formantFrequency(i, 0) / baseFrequency) + 0.5));
    setRatio(1, (int) ((temp2 * Phonemes.formantFrequency(i, 1) / baseFrequency) + 0.5));
    setRatio(2, (int) ((temp2 * Phonemes.formantFrequency(i, 2) / baseFrequency) + 0.5));

    gains[0] = baseGains[0];
    gains[1] = baseGains[1];
    gains[2] = baseGains[2];
  }

  /** FMVoices::noteOn — amplitude sets the per-carrier tilt (amp, amp^2, amp^3). */
  @Override
  public void noteOn(float amplitude) {
    setFrequency(baseFrequency);
    tilt[0] = amplitude;
    tilt[1] = amplitude * amplitude;
    tilt[2] = tilt[1] * amplitude;
    keyOn();
  }

  @Override
  protected float compute(float input, long systemTime) {
    double lfo = vibrato.tick(); // single advance; STK reuses vibrato->lastOut() below
    double temp2 = lfo * modDepth * 0.1;
    for (int i = 0; i < 4; i++) {
      if (ratios[i] > 0.0) waves[i].setFrequency(baseFrequency * (1.0 + temp2) * ratios[i]);
    }

    temp2 = lfo; // pure LFO for AM (matches STK's vibrato->lastOut(), no extra advance)
    double temp = (1.0 + opAMs[3] * temp2) * gains[3] * adsr[3].tick() * waves[3].tick();
    twozero.tick(temp);

    waves[0].addPhaseOffset(control1 * temp * mods[0]);
    waves[1].addPhaseOffset(control1 * temp * mods[1]);
    waves[2].addPhaseOffset(control1 * temp * mods[2]);
    waves[3].addPhaseOffset(control2 * twozero.lastOut());

    temp = (1.0 + opAMs[0] * temp2) * gains[0] * tilt[0] * adsr[0].tick() * waves[0].tick();
    temp += (1.0 + opAMs[1] * temp2) * gains[1] * tilt[1] * adsr[1].tick() * waves[1].tick();
    temp += (1.0 + opAMs[2] * temp2) * gains[2] * tilt[2] * adsr[2].tick() * waves[2].tick();

    return (float) (temp * 0.33) * gain;
  }
}
