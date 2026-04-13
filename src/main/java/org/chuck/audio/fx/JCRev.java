package org.chuck.audio.fx;

import org.chuck.audio.filter.AllPass;
import org.chuck.audio.util.StereoUGen;

/** John Chowning Reverb. */
public class JCRev extends StereoUGen {
  private final AllPass[] allpass = new AllPass[3];
  private final Comb[] comb = new Comb[4];
  private final Delay outLeft, outRight;
  private float mix = 0.5f;

  public JCRev(float sampleRate) {
    // Delay lengths adapted for 44.1kHz. Disable auto-registration for internal parts.
    allpass[0] = new AllPass(225, false);
    allpass[1] = new AllPass(556, false);
    allpass[2] = new AllPass(441, false);

    comb[0] = new Comb(1116, false);
    comb[1] = new Comb(1356, false);
    comb[2] = new Comb(1422, false);
    comb[3] = new Comb(1617, false);

    // Set active delay lengths
    allpass[0].delay(225);
    allpass[1].delay(556);
    allpass[2].delay(441);
    comb[0].delay(1116);
    comb[1].delay(1356);
    comb[2].delay(1422);
    comb[3].delay(1617);

    comb[0].setCoefficient(0.891f);
    comb[1].setCoefficient(0.863f);
    comb[2].setCoefficient(0.841f);
    comb[3].setCoefficient(0.822f);

    outLeft = new Delay(100, sampleRate, false);
    outRight = new Delay(157, sampleRate, false);
    outLeft.delay(100);
    outRight.delay(157);
  }

  public void setMix(float mix) {
    this.mix = mix;
  }

  @Override
  protected void computeStereo(float input, long systemTime) {
    float temp = input;
    for (int i = 0; i < 3; i++) {
      temp = allpass[i].tick(temp, systemTime);
    }

    float filtout = 0;
    for (int i = 0; i < 4; i++) {
      filtout += comb[i].tick(temp, systemTime);
    }
    filtout *= 0.25f; // Normalise gain of parallel comb filters

    float dry = input;
    float wetL = outLeft.tick(filtout, systemTime);
    float wetR = outRight.tick(filtout, systemTime);

    lastOutChannels[0] = dry * (1.0f - mix) + wetL * mix;
    lastOutChannels[1] = dry * (1.0f - mix) + wetR * mix;
  }
}
