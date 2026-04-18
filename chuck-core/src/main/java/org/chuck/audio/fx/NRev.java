package org.chuck.audio.fx;

import org.chuck.audio.filter.AllPass;
import org.chuck.audio.util.StereoUGen;

/** NRev: Classic STK Reverb. */
public class NRev extends StereoUGen {
  private final AllPass[] allpass = new AllPass[8];
  private float mix = 0.5f;

  public NRev(float sampleRate) {
    super();
    allpass[0] = new AllPass(143, false);
    allpass[1] = new AllPass(241, false);
    allpass[2] = new AllPass(391, false);
    allpass[3] = new AllPass(511, false);
    allpass[4] = new AllPass(1021, false);
    allpass[5] = new AllPass(1733, false);
    allpass[6] = new AllPass(2511, false);
    allpass[7] = new AllPass(3539, false);

    for (int i = 0; i < 8; i++) {
      allpass[i].setCoefficient(0.7f);
      allpass[i].delay(
          allpass[i].getBlockCache() != null
              ? 0
              : 0); // dummy to trigger init if needed? No, just call delay
    }
    allpass[0].delay(143);
    allpass[1].delay(241);
    allpass[2].delay(391);
    allpass[3].delay(511);
    allpass[4].delay(1021);
    allpass[5].delay(1733);
    allpass[6].delay(2511);
    allpass[7].delay(3539);
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
    float temp = input;
    for (int i = 0; i < 8; i++) {
      temp = allpass[i].tick(temp, systemTime);
    }

    lastOutChannels[0] = left * (1.0f - mix) + temp * mix;
    lastOutChannels[1] = right * (1.0f - mix) + temp * mix;
  }

  @Override
  protected void computeStereo(float input, long systemTime) {
    // Handled by 2-arg version
  }
}
