package org.chuck.audio.osc;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/**
 * Gendy: Iannis Xenakis's Dynamic Stochastic Synthesis generator. Ported from Csound's gendy /
 * gendyx / gendyc opcodes.
 *
 * <p>Supports Linear step interpolation, Power curves step interpolation (rising/falling), and
 * dynamic second-derivative Cubic step interpolation, generating extremely rich organic noise and
 * stochastic synthesizer timbres.
 */
@doc("Iannis Xenakis's Dynamic Stochastic Synthesis generator.")
public class Gendy extends ChuckUGen {

  public static final int MODE_LINEAR = 0;
  public static final int MODE_POWER = 1;
  public static final int MODE_CUBIC = 2;

  private static final double DEFAULT_SRATE = 44100.0;
  private static final float BIPOLAR = 2147483647.0f;
  private static final double DV2_31 = 4.6566128730773925e-10;

  private int mode = MODE_LINEAR;

  // Parameters
  private float amp = 1.0f;
  private int ampdist = 0;
  private int durdist = 0;
  private float adpar = 0.5f;
  private float ddpar = 0.5f;
  private float minfreq = 20.0f;
  private float maxfreq = 1000.0f;
  private float ampscl = 0.05f;
  private float durscl = 0.05f;
  private int initCps = 12;
  private int knum = 12;

  private float curveup = 1.0f;
  private float curvedown = 1.0f;

  // Internal state arrays
  private int points = 12;
  private float[] memamp;
  private float[] memdur;
  private int currentSeed = 1;

  // Phasor & Step values
  private float phase = 1.0f;
  private float currentAmp = 0.0f;
  private float nextamp = 0.0f;
  private float dur = 0.0f;
  private float speed = 100.0f;
  private int index = 0;

  // Cubic second-derivative state values
  private double slope = 0.0;
  private double midpnt = 0.0;
  private double curve = 0.0;
  private int intPhase = 0;

  private double sampleRate = 44100.0;

  public Gendy() {
    super();
    this.sampleRate =
        org.chuck.core.ChuckVM.CURRENT_VM.isBound()
            ? org.chuck.core.ChuckVM.CURRENT_VM.get().getSampleRate()
            : DEFAULT_SRATE;

    this.points = initCps;
    initState();
  }

  @doc("Set stochastic synthesizer mode (0 = Linear, 1 = Power, 2 = Cubic).")
  public void mode(int m) {
    this.mode = Math.max(0, Math.min(2, m));
  }

  public int mode() {
    return mode;
  }

  @doc("Set output amplitude scaling multiplier.")
  public void amp(float a) {
    this.amp = Math.max(0.0f, a);
  }

  public float amp() {
    return amp;
  }

  @doc("Set amplitude variation distribution type (0 to 5).")
  public void ampdist(int d) {
    this.ampdist = Math.max(0, Math.min(5, d));
  }

  public int ampdist() {
    return ampdist;
  }

  @doc("Set duration variation distribution type (0 to 5).")
  public void durdist(int d) {
    this.durdist = Math.max(0, Math.min(5, d));
  }

  public int durdist() {
    return durdist;
  }

  @doc("Set amplitude distribution parameter/curvedness factor (0.0001 to 1.0).")
  public void adpar(float p) {
    this.adpar = Math.max(0.0001f, Math.min(1.0f, p));
  }

  public float adpar() {
    return adpar;
  }

  @doc("Set duration distribution parameter/curvedness factor (0.0001 to 1.0).")
  public void ddpar(float p) {
    this.ddpar = Math.max(0.0001f, Math.min(1.0f, p));
  }

  public float ddpar() {
    return ddpar;
  }

  @doc("Set minimum control point frequency limit (Hz).")
  public void minfreq(float freq) {
    this.minfreq = Math.max(1.0f, freq);
  }

  public float minfreq() {
    return minfreq;
  }

  @doc("Set maximum control point frequency limit (Hz).")
  public void maxfreq(float freq) {
    this.maxfreq = Math.max(1.0f, freq);
  }

  public float maxfreq() {
    return maxfreq;
  }

  @doc("Set amplitude dynamic random step scaler (0.0 to 1.0).")
  public void ampscl(float s) {
    this.ampscl = Math.max(0.0f, s);
  }

  public float ampscl() {
    return ampscl;
  }

  @doc("Set duration dynamic random step scaler (0.0 to 1.0).")
  public void durscl(float s) {
    this.durscl = Math.max(0.0f, s);
  }

  public float durscl() {
    return durscl;
  }

  @doc("Set active number of control points to cycle through (1 to initial points).")
  public void knum(int num) {
    this.knum = Math.max(1, Math.min(points, num));
  }

  public int knum() {
    return knum;
  }

  @doc("Set power rising curve exponent (power mode only).")
  public void curveup(float c) {
    this.curveup = Math.max(0.0f, c);
  }

  public float curveup() {
    return curveup;
  }

  @doc("Set power falling curve exponent (power mode only).")
  public void curvedown(float c) {
    this.curvedown = Math.max(0.0f, c);
  }

  public float curvedown() {
    return curvedown;
  }

  @doc("Set initial random seed value.")
  public void seed(int s) {
    this.currentSeed = Math.max(1, s);
    initState();
  }

  private int nextRand(int seed) {
    long temp = (long) seed * 16807L;
    return (int) (temp % 2147483647L);
  }

  private void initState() {
    memamp = new float[points];
    memdur = new float[points];
    int seed = currentSeed;
    for (int i = 0; i < points; i++) {
      seed = nextRand(seed);
      long temp = ((long) seed << 1) - 2147483647L;
      memamp[i] = (float) (temp * DV2_31);

      seed = nextRand(seed);
      memdur[i] = (float) (seed * DV2_31);
    }
    currentSeed = seed;

    currentAmp = 0.0f;
    nextamp = 0.0f;
    phase = 1.0f;
    speed = 100.0f;
    index = 0;

    slope = 0.0;
    midpnt = 0.0;
    curve = 0.0;
    intPhase = 0;
  }

  private float gendyDistribution(int which, float a, int rnd) {
    float c, r;
    if (a > 1.0f) a = 1.0f;
    else if (a < 0.0001f) a = 0.0001f;

    switch (which) {
      case 0: // linear
        break;
      case 1: // cauchy
        c = (float) Math.atan(10.0 * a);
        r = (float) (((long) rnd << 1) - 2147483647L) * (float) DV2_31;
        r = (1.0f / a) * (float) Math.tan(c * r) * 0.1f;
        return r;
      case 2: // logistic
        c = 0.5f + (0.499f * a);
        c = (float) Math.log((1.0f - c) / c);
        r = (float) (((double) rnd * DV2_31 - 0.5) * 0.998 * a) + 0.5f;
        r = (float) Math.log((1.0f - r) / r) / c;
        return r;
      case 3: // hyperbolic cosine
        c = (float) Math.tan(1.5692255 * a);
        r = (float) Math.tan(1.5692255 * a * (double) rnd * DV2_31) / c;
        r = (float) (Math.log(r * 0.999f + 0.001f) * -0.1447648) * 2.0f - 1.0f;
        return r;
      case 4: // arcsine
        c = (float) Math.sin(1.5707963 * a);
        r = (float) Math.sin(Math.PI * ((double) rnd * DV2_31 - 0.5) * a) / c;
        return r;
      case 5: // exponential
        c = (float) Math.log(1.0f - (0.999f * a));
        r = (float) ((double) rnd * DV2_31 * 0.999 * a);
        r = (float) (Math.log(1.0f - r) / c) * 2.0f - 1.0f;
        return r;
      default:
        break;
    }
    r = (float) (((long) rnd << 1) - 2147483647L) * (float) DV2_31;
    return r;
  }

  @Override
  protected float compute(float input, long systemTime) {
    int activeKnum = Math.max(1, Math.min(points, knum));

    if (mode == MODE_CUBIC) {
      if (intPhase <= 0) {
        int idx = index;
        if (activeKnum > points || activeKnum < 1) {
          activeKnum = points;
        }
        index = idx = (idx + 1) % activeKnum;
        currentAmp = nextamp;

        currentSeed = nextRand(currentSeed);
        float dist = gendyDistribution(ampdist, adpar, currentSeed);
        nextamp = memamp[idx] + ampscl * dist;

        // Mirror amplitude bounds
        if (nextamp < -1.0f || nextamp > 1.0f) {
          if (nextamp < 0.0f) nextamp += 4.0f;
          nextamp = nextamp % 4.0f;
          if (nextamp > 1.0f) {
            nextamp = (nextamp < 3.0f ? 2.0f - nextamp : nextamp - 4.0f);
          }
        }
        double next_midpnt = (currentAmp + nextamp) * 0.5;
        memamp[idx] = nextamp;

        currentSeed = nextRand(currentSeed);
        dist = gendyDistribution(durdist, ddpar, currentSeed);
        dur = memdur[idx] + durscl * dist;

        // Mirror duration bounds
        if (dur > 1.0f) {
          dur = 2.0f - (dur % 2.0f);
        } else if (dur < 0.0f) {
          dur = 2.0f - ((dur + 2.0f) % 2.0f);
        }
        memdur[idx] = dur;

        double fphase = (minfreq + (maxfreq - minfreq) * dur) * activeKnum;
        if (fphase < 0.001) fphase = 0.001;
        intPhase = (int) (sampleRate / fphase);
        if (intPhase < 2) intPhase = 2;

        curve = 2.0 * (next_midpnt - midpnt - intPhase * slope);
        curve = curve / (double) (intPhase * intPhase + intPhase);
      }

      intPhase--;
      float outVal = (float) (amp * midpnt);
      slope += curve;
      midpnt += slope;
      return input + outVal;

    } else {
      // Linear and Power modes share phasor tracking
      if (phase >= 1.0f) {
        int idx = index;
        phase -= 1.0f;
        if (activeKnum > points || activeKnum < 1) {
          activeKnum = points;
        }
        index = idx = (idx + 1) % activeKnum;
        currentAmp = nextamp;

        currentSeed = nextRand(currentSeed);
        float dist = gendyDistribution(ampdist, adpar, currentSeed);
        nextamp = memamp[idx] + ampscl * dist;

        if (nextamp < -1.0f || nextamp > 1.0f) {
          if (nextamp < 0.0f) nextamp += 4.0f;
          nextamp = nextamp % 4.0f;
          if (nextamp > 1.0f) {
            nextamp = (nextamp < 3.0f ? 2.0f - nextamp : nextamp - 4.0f);
          }
        }
        memamp[idx] = nextamp;

        currentSeed = nextRand(currentSeed);
        dist = gendyDistribution(durdist, ddpar, currentSeed);
        dur = memdur[idx] + durscl * dist;

        if (dur > 1.0f) {
          dur = 2.0f - (dur % 2.0f);
        } else if (dur < 0.0f) {
          dur = 2.0f - ((dur + 2.0f) % 2.0f);
        }
        memdur[idx] = dur;

        speed = (float) ((minfreq + (maxfreq - minfreq) * dur) / sampleRate * activeKnum);
      }

      float outVal;
      if (mode == MODE_POWER) {
        float curveExp =
            (nextamp - currentAmp > 0.0f) ? Math.max(0.0f, curveup) : Math.max(0.0f, curvedown);
        outVal = (float) (currentAmp + Math.pow(phase, curveExp) * (nextamp - currentAmp));
      } else {
        // Standard Linear Interpolation
        outVal = (1.0f - phase) * currentAmp + phase * nextamp;
      }

      phase += speed;
      return input + amp * outVal;
    }
  }
}
