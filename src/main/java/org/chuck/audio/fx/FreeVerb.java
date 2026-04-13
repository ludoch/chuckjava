package org.chuck.audio.fx;

import org.chuck.audio.util.StereoUGen;
import org.chuck.core.doc;

/**
 * FreeVerb: Lush Schroeder-Moorer algorithmic reverb. Uses 8 parallel comb filters and 4 series
 * all-pass filters per channel.
 */
@doc("Lush Schroeder-Moorer algorithmic reverb.")
public class FreeVerb extends StereoUGen {
  private final CombFilter[] combL = new CombFilter[8];
  private final CombFilter[] combR = new CombFilter[8];
  private final AllPassFilter[] allPassL = new AllPassFilter[4];
  private final AllPassFilter[] allPassR = new AllPassFilter[4];

  private float roomSize = 0.5f;
  private float damp = 0.5f;
  private float mix = 0.3f;

  private static final int[] COMB_TUNING_L = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
  private static final int[] COMB_TUNING_R = {1139, 1211, 1300, 1379, 1445, 1514, 1580, 1640};
  private static final int[] ALLPASS_TUNING_L = {556, 441, 341, 225};
  private static final int[] ALLPASS_TUNING_R = {579, 464, 364, 248};

  public FreeVerb() {
    for (int i = 0; i < 8; i++) {
      combL[i] = new CombFilter(COMB_TUNING_L[i]);
      combR[i] = new CombFilter(COMB_TUNING_R[i]);
    }
    for (int i = 0; i < 4; i++) {
      allPassL[i] = new AllPassFilter(ALLPASS_TUNING_L[i]);
      allPassR[i] = new AllPassFilter(ALLPASS_TUNING_R[i]);
    }
    updateParameters();
  }

  @doc("Set room size (0.0 to 1.0).")
  public void roomSize(float r) {
    this.roomSize = r;
    updateParameters();
  }

  @doc("Set damping factor (0.0 to 1.0).")
  public void damp(float d) {
    this.damp = d;
    updateParameters();
  }

  @doc("Set dry/wet mix (0.0 to 1.0).")
  public void mix(float m) {
    this.mix = m;
  }

  private void updateParameters() {
    float feedback = roomSize * 0.28f + 0.7f;
    float damping = damp * 0.4f;
    for (int i = 0; i < 8; i++) {
      combL[i].feedback = feedback;
      combL[i].damp = damping;
      combR[i].feedback = feedback;
      combR[i].damp = damping;
    }
  }

  @Override
  protected void computeStereo(float input, long systemTime) {
    float outL = 0, outR = 0;

    // 1. Parallel Combs
    for (int i = 0; i < 8; i++) {
      outL += combL[i].tick(input);
      outR += combR[i].tick(input);
    }

    // 2. Series All-passes
    for (int i = 0; i < 4; i++) {
      outL = allPassL[i].tick(outL);
      outR = allPassR[i].tick(outR);
    }

    // 3. Dry/Wet Mix
    lastOutChannels[0] = input * (1.0f - mix) + outL * mix;
    lastOutChannels[1] = input * (1.0f - mix) + outR * mix;
  }

  // Internal mini-filters for performance
  private static class CombFilter {
    float[] buffer;
    int pos = 0;
    float feedback = 0.5f;
    float damp = 0.5f;
    float lastStore = 0;

    CombFilter(int size) {
      buffer = new float[size];
    }

    float tick(float input) {
      float output = buffer[pos];
      lastStore = (output * (1.0f - damp)) + (lastStore * damp);
      buffer[pos] = input + (lastStore * feedback);
      pos = (pos + 1) % buffer.length;
      return output;
    }
  }

  private static class AllPassFilter {
    float[] buffer;
    int pos = 0;

    AllPassFilter(int size) {
      buffer = new float[size];
    }

    float tick(float input) {
      float bufOut = buffer[pos];
      float output = -input + bufOut;
      buffer[pos] = input + (bufOut * 0.5f);
      pos = (pos + 1) % buffer.length;
      return output;
    }
  }
}
