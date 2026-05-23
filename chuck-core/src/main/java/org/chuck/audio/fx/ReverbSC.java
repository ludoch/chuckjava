package org.chuck.audio.fx;

import org.chuck.audio.util.StereoUGen;
import org.chuck.core.doc;

/**
 * ReverbSC: High-fidelity 8-delay Feedback Delay Network (FDN) studio-grade reverb. Ported from the
 * original Csound Sean Costello / Istvan Varga reverbsc opcode.
 *
 * <p>Uses 8 pitch-modulated delay lines with cubic interpolation and lowpass dampening, generating
 * premium high-density diffuse natural room fields.
 */
@doc("High-fidelity 8-delay Feedback Delay Network (FDN) studio-grade reverb.")
public class ReverbSC extends StereoUGen {

  private static final double DEFAULT_SRATE = 44100.0;
  private static final double DELAYPOS_SCALE = 268435456.0; // 0x10000000
  private static final int DELAYPOS_SHIFT = 28;
  private static final int DELAYPOS_MASK = 0x0FFFFFFF;

  private static final double[][] REVERB_PARAMS = {
    {(2473.0 / DEFAULT_SRATE), 0.0010, 3.100, 1966.0},
    {(2767.0 / DEFAULT_SRATE), 0.0011, 3.500, 29491.0},
    {(3217.0 / DEFAULT_SRATE), 0.0017, 1.110, 22937.0},
    {(3557.0 / DEFAULT_SRATE), 0.0006, 3.973, 9830.0},
    {(3907.0 / DEFAULT_SRATE), 0.0010, 2.341, 20643.0},
    {(4127.0 / DEFAULT_SRATE), 0.0011, 1.897, 22937.0},
    {(2143.0 / DEFAULT_SRATE), 0.0017, 0.891, 29491.0},
    {(1933.0 / DEFAULT_SRATE), 0.0006, 3.221, 14417.0}
  };

  private static final double OUTPUT_GAIN = 0.35;
  private static final double JP_SCALE = 0.25;

  private static class DelayLine {
    int writePos = 0;
    int bufferSize;
    int readPos = 0;
    int readPosFrac = 0;
    int readPosFrac_inc = 0;
    int seedVal = 0;
    int randLine_cnt = 0;
    double filterState = 0.0;
    float[] buf;
  }

  private final DelayLine[] delayLines = new DelayLine[8];

  private double feedback = 0.85;
  private double lpFreq = 10000.0;
  private double sampleRate = 44100.0;
  private double pitchMod = 1.0;
  private double dampFact = 1.0;
  private double prv_LPFreq = 0.0;
  private float mix = 0.3f;

  public ReverbSC() {
    this.sampleRate =
        org.chuck.core.ChuckVM.CURRENT_VM.isBound()
            ? org.chuck.core.ChuckVM.CURRENT_VM.get().getSampleRate()
            : DEFAULT_SRATE;

    for (int i = 0; i < 8; i++) {
      delayLines[i] = new DelayLine();
      initDelayLine(i);
    }
  }

  @doc("Set feedback gain (0.0 to 1.0). Controls reverb decay time.")
  public void feedback(float f) {
    this.feedback = Math.max(0.0, Math.min(1.0, f));
  }

  public float feedback() {
    return (float) feedback;
  }

  @doc("Set lowpass filter cutoff frequency in Hz. Controls high frequency damping.")
  public void lpFreq(float freq) {
    this.lpFreq = Math.max(20.0, Math.min(20000.0, freq));
  }

  public float lpFreq() {
    return (float) lpFreq;
  }

  @doc("Set pitch modulation depth multiplier (0.0 to 20.0). Controls chorusing/widening.")
  public void pitchMod(float m) {
    double old = this.pitchMod;
    this.pitchMod = Math.max(0.0, Math.min(20.0, m));
    if (this.pitchMod != old) {
      // Re-initialize delay buffers to avoid bounds exceptions if max limits shift
      for (int i = 0; i < 8; i++) {
        initDelayLine(i);
      }
    }
  }

  public float pitchMod() {
    return (float) pitchMod;
  }

  @doc("Set dry/wet mix (0.0 to 1.0).")
  public void mix(float m) {
    this.mix = Math.max(0.0f, Math.min(1.0f, m));
  }

  public float mix() {
    return mix;
  }

  private int delayLineMaxSamples(int n) {
    double maxDel = REVERB_PARAMS[n][0];
    maxDel += (REVERB_PARAMS[n][1] * pitchMod * 1.125);
    return (int) (maxDel * sampleRate + 16.5);
  }

  private void initDelayLine(int n) {
    DelayLine lp = delayLines[n];
    lp.bufferSize = delayLineMaxSamples(n);
    lp.writePos = 0;
    lp.seedVal = (int) (REVERB_PARAMS[n][3] + 0.5);

    double readPos = (double) lp.seedVal * REVERB_PARAMS[n][1] / 32768.0;
    readPos = REVERB_PARAMS[n][0] + (readPos * pitchMod);
    readPos = (double) lp.bufferSize - (readPos * sampleRate);
    lp.readPos = (int) readPos;
    readPos = (readPos - (double) lp.readPos) * DELAYPOS_SCALE;
    lp.readPosFrac = (int) (readPos + 0.5);

    nextRandomLineSeg(n, lp);
    lp.filterState = 0.0;
    lp.buf = new float[lp.bufferSize];
  }

  private void nextRandomLineSeg(int n, DelayLine lp) {
    if (lp.seedVal < 0) {
      lp.seedVal += 0x10000;
    }
    lp.seedVal = (lp.seedVal * 15625 + 1) & 0xFFFF;
    if (lp.seedVal >= 0x8000) {
      lp.seedVal -= 0x10000;
    }

    lp.randLine_cnt = (int) ((sampleRate / REVERB_PARAMS[n][2]) + 0.5);

    double prvDel = lp.writePos;
    prvDel -= ((double) lp.readPos + ((double) lp.readPosFrac / DELAYPOS_SCALE));
    while (prvDel < 0.0) {
      prvDel += lp.bufferSize;
    }
    prvDel = prvDel / sampleRate;

    double nxtDel = (double) lp.seedVal * REVERB_PARAMS[n][1] / 32768.0;
    nxtDel = REVERB_PARAMS[n][0] + (nxtDel * pitchMod);

    double phs_incVal = (prvDel - nxtDel) / (double) lp.randLine_cnt;
    phs_incVal = phs_incVal * sampleRate + 1.0;
    lp.readPosFrac_inc = (int) (phs_incVal * DELAYPOS_SCALE + 0.5);
  }

  @Override
  protected void computeStereo(float left, float right, long systemTime) {
    // 1. Update filter coefficient if frequency changed
    if (lpFreq != prv_LPFreq) {
      prv_LPFreq = lpFreq;
      double tempDamp = 2.0 - Math.cos(prv_LPFreq * 2.0 * Math.PI / sampleRate);
      dampFact = tempDamp - Math.sqrt(tempDamp * tempDamp - 1.0);
    }

    // 2. Calculate junction pressure
    double ainL = 0.0;
    for (int n = 0; n < 8; n++) {
      ainL += delayLines[n].filterState;
    }
    ainL *= JP_SCALE;

    double ainR = ainL + right;
    ainL = ainL + left;

    double aoutL = 0.0;
    double aoutR = 0.0;

    // 3. Process each delay line
    for (int n = 0; n < 8; n++) {
      DelayLine lp = delayLines[n];
      int bufferSize = lp.bufferSize;

      // Write feedback to delay line
      lp.buf[lp.writePos] = (float) ((n % 2 != 0 ? ainR : ainL) - lp.filterState);
      if (++lp.writePos >= bufferSize) {
        lp.writePos -= bufferSize;
      }

      // Read from delay line with cubic interpolation
      if (lp.readPosFrac >= (int) DELAYPOS_SCALE) {
        lp.readPos += (lp.readPosFrac >> DELAYPOS_SHIFT);
        lp.readPosFrac &= DELAYPOS_MASK;
      }
      if (lp.readPos >= bufferSize) {
        lp.readPos -= bufferSize;
      }
      int readPos = lp.readPos;
      double frac = (double) lp.readPosFrac * (1.0 / DELAYPOS_SCALE);

      // Cubic interpolation coefficients
      double a2 = frac * frac;
      a2 -= 1.0;
      a2 *= (1.0 / 6.0);
      double a1 = frac;
      a1 += 1.0;
      a1 *= 0.5;
      double am1 = a1 - 1.0;
      double a0 = 3.0 * a2;
      a1 -= a0;
      am1 -= a2;
      a0 -= frac;

      double vm1, v0, v1, v2;
      if (readPos > 0 && readPos < (bufferSize - 2)) {
        vm1 = lp.buf[readPos - 1];
        v0 = lp.buf[readPos];
        v1 = lp.buf[readPos + 1];
        v2 = lp.buf[readPos + 2];
      } else {
        int rp = readPos - 1;
        if (rp < 0) rp += bufferSize;
        vm1 = lp.buf[rp];

        rp = readPos;
        v0 = lp.buf[rp];

        rp = readPos + 1;
        if (rp >= bufferSize) rp -= bufferSize;
        v1 = lp.buf[rp];

        rp = readPos + 2;
        if (rp >= bufferSize) rp -= bufferSize;
        v2 = lp.buf[rp];
      }
      double v = (am1 * vm1 + a0 * v0 + a1 * v1 + a2 * v2) * frac + v0;

      // Update fractional read head position
      lp.readPosFrac += lp.readPosFrac_inc;

      // Apply feedback gain and lowpass dampening filter
      v *= feedback;
      v = (lp.filterState - v) * dampFact + v;
      lp.filterState = v;

      // Mix to stereo output channels (split even and odd lines)
      if (n % 2 != 0) {
        aoutR += v;
      } else {
        aoutL += v;
      }

      // Transition to next random line segment if current finishes
      if (--(lp.randLine_cnt) <= 0) {
        nextRandomLineSeg(n, lp);
      }
    }

    // 4. Mix Dry/Wet channels
    lastOutChannels[0] = (float) (left * (1.0f - mix) + aoutL * OUTPUT_GAIN * mix);
    lastOutChannels[1] = (float) (right * (1.0f - mix) + aoutR * OUTPUT_GAIN * mix);
  }

  @Override
  protected void computeStereo(float input, long systemTime) {
    computeStereo(input, input, systemTime);
  }
}
