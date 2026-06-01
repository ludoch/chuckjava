package org.chuck.audio.stk.fm;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.ChuckVM;

/**
 * Abstract base for STK's FM voices (FM.cpp), shared by BeeThree, Wurley, Rhodey, TubeBell,
 * HevyMetl, PercFlut and FMVoices. Holds the four operators ({@link FmWaveLoop} + {@link StkAdsr}),
 * a vibrato wave, a feedback {@link StkTwoZero}, the operator ratio/gain arrays and the {@code
 * __FM_gains} lookup table — all ported verbatim from ChucK so subclasses can reproduce STK's
 * per-sample algorithm exactly.
 *
 * <p>Concrete voices set up {@code waves[]}, {@code ratios}, {@code gains}/{@code baseGains} and
 * each operator's ADSR in their constructor, then implement {@link #compute} with the voice's FM
 * routing.
 */
public abstract class FmInstrument extends ChuckUGen {
  protected static final int N_OPERATORS = 4;

  /** __FM_gains[i]: 100 values, gains[99]=1, each prior *= 0.933033 (built top-down). */
  protected static final double[] FM_GAINS = new double[100];

  static {
    double temp = 1.0;
    for (int i = 99; i >= 0; i--) {
      FM_GAINS[i] = temp;
      temp *= 0.933033;
    }
  }

  protected final double sampleRate;
  protected final FmWaveLoop[] waves = new FmWaveLoop[N_OPERATORS];
  protected final StkAdsr[] adsr = new StkAdsr[N_OPERATORS];
  protected final double[] ratios = new double[N_OPERATORS];
  protected final double[] gains = new double[N_OPERATORS];
  protected final double[] baseGains = new double[N_OPERATORS];
  protected final double[] opAMs = new double[N_OPERATORS];

  protected final FmWaveLoop vibrato;
  protected final StkTwoZero twozero = new StkTwoZero();

  protected double modDepth = 0.0;
  protected double control1 = 1.0;
  protected double control2 = 1.0;
  protected double baseFrequency = 440.0;

  protected FmInstrument(float sampleRate) {
    this.sampleRate = sampleRate;
    for (int i = 0; i < N_OPERATORS; i++) {
      ratios[i] = 1.0;
      gains[i] = 1.0;
      baseGains[i] = 1.0;
      opAMs[i] = 0.0;
      adsr[i] = new StkAdsr(sampleRate);
    }
    twozero.setB2(-1.0);
    twozero.setGain(0.0);
    vibrato = new FmWaveLoop(Rawwaves.SINEWAVE, sampleRate);
    vibrato.setFrequency(6.0);
  }

  protected FmInstrument() {
    this(ChuckVM.CURRENT_VM.get().getSampleRate());
  }

  /** FM::setFrequency — set base frequency and propagate to all operators by ratio. */
  public void setFrequency(double frequency) {
    baseFrequency = frequency;
    for (int i = 0; i < N_OPERATORS; i++) waves[i].setFrequency(baseFrequency * ratios[i]);
  }

  /** FM::setRatio — set operator ratio and update that operator's frequency. */
  protected void setRatio(int waveIndex, double ratio) {
    if (waveIndex < 0 || waveIndex >= N_OPERATORS) return;
    ratios[waveIndex] = ratio;
    if (ratio > 0.0) waves[waveIndex].setFrequency(baseFrequency * ratio);
    else waves[waveIndex].setFrequency(ratio);
  }

  protected void keyOn() {
    for (StkAdsr a : adsr) a.keyOn();
  }

  protected void keyOff() {
    for (StkAdsr a : adsr) a.keyOff();
  }

  protected void setModulationDepth(double depth) {
    modDepth = depth;
  }

  // ── ChucK control surface (names preserved for reflection-based dispatch) ──────────────────

  /** {@code freq => m.freq} */
  public void freq(float f) {
    setFrequency(f);
  }

  /** {@code m.setFreq(f)} */
  public void setFreq(double f) {
    setFrequency(f);
  }

  /** {@code amp => m.noteOn} — scale operator gains by amplitude and key on all envelopes. */
  public void noteOn(float amplitude) {
    for (int i = 0; i < N_OPERATORS; i++) gains[i] = amplitude * baseGains[i];
    keyOn();
  }

  /** {@code amp => m.noteOff} */
  public void noteOff(float amplitude) {
    keyOff();
  }
}
