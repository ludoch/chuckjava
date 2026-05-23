package org.chuck.audio.fx;

import org.chuck.audio.util.StereoUGen;
import org.chuck.core.doc;

/**
 * FreeVerb: Lush Schroeder-Moorer algorithmic reverb. Uses 8 parallel feedback comb filters and 4
 * series all-pass filters per channel, with sample-rate independent damping correction and distinct
 * stereo spread delays.
 */
@doc("Lush Schroeder-Moorer algorithmic reverb.")
public class FreeVerb extends StereoUGen {

  private static final double DEFAULT_SRATE = 44100.0;
  private static final double STEREO_SPREAD = 23.0;

  private static final double[] COMB_DELAYS = {
    1116.0 / DEFAULT_SRATE,
    1188.0 / DEFAULT_SRATE,
    1277.0 / DEFAULT_SRATE,
    1356.0 / DEFAULT_SRATE,
    1422.0 / DEFAULT_SRATE,
    1491.0 / DEFAULT_SRATE,
    1557.0 / DEFAULT_SRATE,
    1617.0 / DEFAULT_SRATE
  };

  private static final double[] ALLPASS_DELAYS = {
    556.0 / DEFAULT_SRATE, 441.0 / DEFAULT_SRATE, 341.0 / DEFAULT_SRATE, 225.0 / DEFAULT_SRATE
  };

  private static final double SCALE_ROOM = 0.28;
  private static final double OFFSET_ROOM = 0.7;
  private static final double SCALE_DAMP = 0.4;
  private static final double ALLPASS_FEEDBACK = 0.5;
  private static final double FIXED_GAIN = 0.015;

  private static class CombFilter {
    float[] buf;
    int pos = 0;
    int size;
    double filterState = 0.0;

    CombFilter(int size) {
      this.size = size;
      this.buf = new float[size];
    }
  }

  private static class AllPassFilter {
    float[] buf;
    int pos = 0;
    int size;

    AllPassFilter(int size) {
      this.size = size;
      this.buf = new float[size];
    }
  }

  private final CombFilter[] combL = new CombFilter[8];
  private final CombFilter[] combR = new CombFilter[8];
  private final AllPassFilter[] allPassL = new AllPassFilter[4];
  private final AllPassFilter[] allPassR = new AllPassFilter[4];

  private float roomSize = 0.5f;
  private float damp = 0.5f;
  private float mix = 0.3f;

  private double sampleRate = 44100.0;
  private double srFact = 1.0;
  private double feedback = 0.84;
  private double damp1 = 0.2;
  private double damp2 = 0.8;
  private double dampValue = 0.2;
  private float prvDampFactor = -1.0f;

  public FreeVerb() {
    this.sampleRate =
        org.chuck.core.ChuckVM.CURRENT_VM.isBound()
            ? org.chuck.core.ChuckVM.CURRENT_VM.get().getSampleRate()
            : DEFAULT_SRATE;

    this.srFact = Math.pow(DEFAULT_SRATE / sampleRate, 0.8);

    // Initialize Comb Filters
    for (int i = 0; i < 8; i++) {
      int sizeL = (int) (COMB_DELAYS[i] * sampleRate + 0.5);
      int sizeR = (int) ((COMB_DELAYS[i] + STEREO_SPREAD / DEFAULT_SRATE) * sampleRate + 0.5);
      combL[i] = new CombFilter(sizeL);
      combR[i] = new CombFilter(sizeR);
    }

    // Initialize All-pass Filters
    for (int i = 0; i < 4; i++) {
      int sizeL = (int) (ALLPASS_DELAYS[i] * sampleRate + 0.5);
      int sizeR = (int) ((ALLPASS_DELAYS[i] + STEREO_SPREAD / DEFAULT_SRATE) * sampleRate + 0.5);
      allPassL[i] = new AllPassFilter(sizeL);
      allPassR[i] = new AllPassFilter(sizeR);
    }

    updateParameters();
  }

  @doc("Set room size (0.0 to 1.0). Controls reverb decay time.")
  public void roomSize(float r) {
    this.roomSize = Math.max(0.0f, Math.min(1.0f, r));
    updateParameters();
  }

  public float roomSize() {
    return roomSize;
  }

  @doc("Set damping factor (0.0 to 1.0). Controls high frequency absorption.")
  public void damp(float d) {
    this.damp = Math.max(0.0f, Math.min(1.0f, d));
    updateParameters();
  }

  public float damp() {
    return damp;
  }

  @doc("Set dry/wet mix (0.0 to 1.0).")
  public void mix(float m) {
    this.mix = Math.max(0.0f, Math.min(1.0f, m));
  }

  public float mix() {
    return mix;
  }

  private void updateParameters() {
    feedback = roomSize * SCALE_ROOM + OFFSET_ROOM;
    if (damp != prvDampFactor) {
      prvDampFactor = damp;
      double dVal = damp * SCALE_DAMP;
      dampValue = Math.pow(dVal, srFact);
    }
    damp1 = dampValue;
    damp2 = 1.0 - damp1;
  }

  @Override
  protected void computeStereo(float left, float right, long systemTime) {
    // ── Left Channel ──
    double outL = 0.0;
    for (int i = 0; i < 8; i++) {
      CombFilter comb = combL[i];
      double x = comb.buf[comb.pos];
      outL += x;

      // One-pole lowpass damping feedback path
      comb.filterState = (comb.filterState * damp1) + (x * damp2);
      comb.buf[comb.pos] = (float) (comb.filterState * feedback + left);
      if (++comb.pos >= comb.size) {
        comb.pos = 0;
      }
    }

    for (int i = 0; i < 4; i++) {
      AllPassFilter ap = allPassL[i];
      double bufOut = ap.buf[ap.pos];
      double x = bufOut - outL;

      ap.buf[ap.pos] = (float) (outL + bufOut * ALLPASS_FEEDBACK);
      if (++ap.pos >= ap.size) {
        ap.pos = 0;
      }
      outL = x;
    }

    // ── Right Channel ──
    double outR = 0.0;
    for (int i = 0; i < 8; i++) {
      CombFilter comb = combR[i];
      double x = comb.buf[comb.pos];
      outR += x;

      comb.filterState = (comb.filterState * damp1) + (x * damp2);
      comb.buf[comb.pos] = (float) (comb.filterState * feedback + right);
      if (++comb.pos >= comb.size) {
        comb.pos = 0;
      }
    }

    for (int i = 0; i < 4; i++) {
      AllPassFilter ap = allPassR[i];
      double bufOut = ap.buf[ap.pos];
      double x = bufOut - outR;

      ap.buf[ap.pos] = (float) (outR + bufOut * ALLPASS_FEEDBACK);
      if (++ap.pos >= ap.size) {
        ap.pos = 0;
      }
      outR = x;
    }

    // Apply fixed gain and blend wet/dry mix
    float wetL = (float) (outL * FIXED_GAIN);
    float wetR = (float) (outR * FIXED_GAIN);

    lastOutChannels[0] = left * (1.0f - mix) + wetL * mix;
    lastOutChannels[1] = right * (1.0f - mix) + wetR * mix;
  }

  @Override
  protected void computeStereo(float input, long systemTime) {
    computeStereo(input, input, systemTime);
  }
}
