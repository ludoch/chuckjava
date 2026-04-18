package org.chuck.audio.fx;

import org.chuck.audio.filter.AllPass;
import org.chuck.audio.util.StereoUGen;

/** PRCRev: Perry R. Cook's Reverb. */
public class PRCRev extends StereoUGen {
  private final AllPass[] allpass = new AllPass[2];
  private final Comb[] comb = new Comb[2];
  private float mix = 0.5f;

  public PRCRev(float sampleRate) {
    super();
    allpass[0] = new AllPass(353, false);
    allpass[1] = new AllPass(1097, false);
    comb[0] = new Comb(1777, false);
    comb[1] = new Comb(2137, false);

    allpass[0].delay(353);
    allpass[1].delay(1097);
    comb[0].delay(1777);
    comb[1].delay(2137);

    allpass[0].setCoefficient(0.7f);
    allpass[1].setCoefficient(0.7f);
    comb[0].setCoefficient(0.8f);
    comb[1].setCoefficient(0.8f);
  }

  public void mix(float m) {
    this.mix = m;
  }

  public float mix() {
    return mix;
  }

  @Override
  protected void computeStereo(float left, float right, long systemTime) {
    float input = (left + right) * 0.5f;
    float temp = allpass[0].tick(input, systemTime);
    temp = allpass[1].tick(temp, systemTime);

    float wet = (comb[0].tick(temp, systemTime) + comb[1].tick(temp, systemTime)) * 0.5f;

    lastOutChannels[0] = left * (1.0f - mix) + wet * mix;
    lastOutChannels[1] = right * (1.0f - mix) + wet * mix;
  }

  @Override
  protected void computeStereo(float input, long systemTime) {
    // Handled by 2-arg version
  }
}
