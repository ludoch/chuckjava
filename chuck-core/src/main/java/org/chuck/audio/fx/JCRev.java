package org.chuck.audio.fx;

import org.chuck.audio.filter.AllPass;
import org.chuck.audio.util.StereoUGen;

/** John Chowning Reverb. Matches STK JCRev behavior. */
public class JCRev extends StereoUGen {
  private final AllPass[] allpass = new AllPass[3];
  private final Comb[] comb = new Comb[4];
  private final Delay outLeft, outRight;
  private float mix = 0.3f; // STK default effectMix_

  public JCRev() {
    this(org.chuck.core.ChuckVM.CURRENT_VM.get().getSampleRate());
  }

  public JCRev(float sampleRate) {
    // Delay lengths for 44.1kHz from STK
    allpass[0] = new AllPass(225, false);
    allpass[1] = new AllPass(341, false);
    allpass[2] = new AllPass(441, false);

    comb[0] = new Comb(1116, false);
    comb[1] = new Comb(1356, false);
    comb[2] = new Comb(1422, false);
    comb[3] = new Comb(1617, false);

    for (int i = 0; i < 3; i++) allpass[i].setCoefficient(0.7);
    for (int i = 0; i < 4; i++) comb[i].setPole(0.2);

    outLeft = new Delay(211, sampleRate, false);
    outRight = new Delay(179, sampleRate, false);
    outLeft.delay(211);
    outRight.delay(179);

    setT60(1.0); // Default T60
  }

  public void setT60(double t60) {
    float sr = org.chuck.core.ChuckVM.CURRENT_VM.get().getSampleRate();
    // From STK: combCoefficient_[i] = pow(10.0, (-3.0 * combDelays_[i].getDelay() / (T60 *
    // Stk::sampleRate())));
    comb[0].setCoefficient(Math.pow(10.0, -3.0 * 1116 / (t60 * sr)));
    comb[1].setCoefficient(Math.pow(10.0, -3.0 * 1356 / (t60 * sr)));
    comb[2].setCoefficient(Math.pow(10.0, -3.0 * 1422 / (t60 * sr)));
    comb[3].setCoefficient(Math.pow(10.0, -3.0 * 1617 / (t60 * sr)));
  }

  public void mix(double mix) {
    this.mix = (float) mix;
  }

  public void mix(float mix) {
    this.mix = mix;
  }

  public float mix() {
    return mix;
  }

  @Override
  protected void computeStereo(float left, float right, long systemTime) {
    // Mono-sum for the reverb tail (standard Schroeder reverb behavior)
    // By fixing MultiChannelUGen/StereoUGen, 'left' and 'right' are now correct!
    float input = (left + right) * 0.5f;

    // Series AllPass
    float temp = input;
    for (int i = 0; i < 3; i++) {
      temp = allpass[i].tick(temp, systemTime);
    }

    // Parallel Combs
    float filtout = 0;
    for (int i = 0; i < 4; i++) {
      filtout += comb[i].tick(temp, systemTime);
    }

    // Preserve original stereo dry signal
    float wetL = outLeft.tick(filtout, systemTime);
    float wetR = outRight.tick(filtout, systemTime);

    lastOutChannels[0] = left * (1.0f - mix) + wetL * mix;
    lastOutChannels[1] = right * (1.0f - mix) + wetR * mix;
  }

  @Override
  protected void computeStereo(float input, long systemTime) {
    computeStereo(input, input, systemTime);
  }
}
