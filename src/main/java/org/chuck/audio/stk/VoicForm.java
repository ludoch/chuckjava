package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.filter.BPF;
import org.chuck.audio.osc.SinOsc;
import org.chuck.audio.util.Adsr;

/**
 * VoicForm — 4-formant singing voice synthesizer. A buzz (pulsed glottal wave) drives 4 bandpass
 * resonators tuned to formant frequencies that define the vowel quality.
 *
 * <p>Formant presets (phoneme index via phoneme()): 0="eee", 1="ihh", 2="ehh", 3="aaa", 4="ahh",
 * 5="aww", 6="ohh", 7="uhh"
 */
public class VoicForm extends ChuckUGen {
  // 4 formant frequencies (Hz) per vowel: [F1, F2, F3, F4]
  private static final double[][] FORMANTS = {
    {270, 2290, 3010, 3500}, // eee
    {390, 1990, 2550, 3550}, // ihh
    {530, 1840, 2480, 3500}, // ehh
    {660, 1720, 2410, 3300}, // aaa
    {730, 1090, 2440, 3400}, // ahh
    {570, 840, 2410, 3300}, // aww
    {490, 920, 2490, 3500}, // ohh
    {490, 1350, 1690, 3500}, // uhh
  };
  private static final double[][] FORMANT_GAINS = {
    {0.9, 0.5, 0.3, 0.1},
    {0.9, 0.5, 0.3, 0.1},
    {0.9, 0.5, 0.3, 0.1},
    {0.9, 0.6, 0.3, 0.1},
    {0.9, 0.6, 0.3, 0.1},
    {0.9, 0.5, 0.3, 0.1},
    {0.9, 0.5, 0.3, 0.1},
    {0.9, 0.5, 0.2, 0.1},
  };

  private final BPF[] filters = new BPF[4];
  private final Adsr env;
  private final SinOsc buzz;
  private double freq = 220.0;
  private int phonemeIdx = 0;
  private final float sampleRate;

  public VoicForm(float sr) {
    this.sampleRate = sr;
    for (int i = 0; i < 4; i++) {
      filters[i] = new BPF(sr);
      filters[i].Q(20.0); // narrow resonance
    }
    buzz = new SinOsc(sr);
    env = new Adsr(sr);
    env.set(0.05f, 0.02f, 0.9f, 0.1f);
    setFreq(220.0);
    phoneme(0);
  }

  public void setFreq(double f) {
    freq = f;
    buzz.setFreq(f);
  }

  /** Set vowel by index 0–7. */
  public void phoneme(int idx) {
    phonemeIdx = Math.max(0, Math.min(7, idx));
    double[] ff = FORMANTS[phonemeIdx];
    for (int i = 0; i < 4; i++) filters[i].freq(ff[i]);
  }

  public void noteOn(float v) {
    env.keyOn();
  }

  public void noteOff(float v) {
    env.keyOff();
  }

  @Override
  protected float compute(float input, long t) {
    float e = env.tick(t);
    // Glottal excitation: buzz signal (simplified pulsed source)
    float src = buzz.tick(t) * e;
    double[] gains = FORMANT_GAINS[phonemeIdx];
    float out = 0;
    for (int i = 0; i < 4; i++) {
      out += filters[i].tick(src, t) * (float) gains[i];
    }
    out *= gain;
    lastOut = out;
    return out;
  }
}
