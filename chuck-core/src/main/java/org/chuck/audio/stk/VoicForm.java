package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.filter.BPF;
import org.chuck.audio.filter.OnePole;
import org.chuck.audio.osc.Noise;
import org.chuck.audio.osc.SinOsc;
import org.chuck.audio.util.Adsr;
import org.chuck.audio.util.Wavetable;
import org.chuck.audio.util.WavetableRegistry;
import org.chuck.core.doc;

/**
 * VoicForm — 4-formant singing voice synthesizer. Based on STK VoicForm class. Uses a glottal pulse
 * excitation (wavetable) driving 4 bandpass resonators.
 */
@doc("Singing voice physical model using glottal pulse wavetable and formant filters.")
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
    {1.0, 0.5, 0.2, 0.1},
    {1.0, 0.5, 0.2, 0.1},
    {1.0, 0.5, 0.2, 0.1},
    {1.0, 0.6, 0.2, 0.1},
    {1.0, 0.6, 0.2, 0.1},
    {1.0, 0.5, 0.2, 0.1},
    {1.0, 0.5, 0.2, 0.1},
    {1.0, 0.5, 0.2, 0.1},
  };

  private final BPF[] filters = new BPF[4];
  private final OnePole tiltFilter;
  private final Adsr env;
  private final Wavetable glottis;
  private final Noise breath;
  private final SinOsc vibrato;

  private double freq = 220.0;
  private float voicedMix = 1.0f;
  private float unvoicedMix = 0.1f;
  private float vibratoDepth = 0.05f;
  private int phonemeIdx = 0;
  private final float sampleRate;

  public VoicForm(float sr) {
    this.sampleRate = sr;
    for (int i = 0; i < 4; i++) {
      filters[i] = new BPF(sr);
      filters[i].Q(20.0);
    }

    tiltFilter = new OnePole(sr);
    tiltFilter.setPole(0.9f);

    glottis = new Wavetable();
    glottis.setTable(WavetableRegistry.getGlottalPulse());
    glottis.loop(1);

    breath = new Noise();
    vibrato = new SinOsc(sr);
    vibrato.setFreq(6.0);

    env = new Adsr(sr);
    env.set(0.05f, 0.02f, 0.9f, 0.1f);

    setFreq(220.0);
    phoneme(0);
  }

  @doc("Set the fundamental frequency (pitch) of the voice.")
  public void freq(double f) {
    freq = f;
    glottis.rate(f * 256.0 / sampleRate);
  }

  public void setFreq(double f) {
    freq(f);
  }

  @doc("Set vowel by index (0-7). 0:eee, 4:ahh, 6:ohh.")
  public void phoneme(int idx) {
    phonemeIdx = Math.max(0, Math.min(7, idx));
    double[] ff = FORMANTS[phonemeIdx];
    for (int i = 0; i < 4; i++) filters[i].freq(ff[i]);
  }

  @doc("Set the amount of voiced (vocal cord) signal.")
  public void voiced(float v) {
    this.voicedMix = v;
  }

  @doc("Set the amount of unvoiced (breath) signal.")
  public void unvoiced(float v) {
    this.unvoicedMix = v;
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

    // Vibrato
    float vib = (float) (1.0 + vibrato.tick(t) * vibratoDepth);
    glottis.rate(freq * vib * 256.0 / sampleRate);

    // Excitation = Voiced + Unvoiced
    float voicedPart = glottis.tick(t) * voicedMix;
    float unvoicedPart = breath.tick(t) * unvoicedMix;
    float src = (voicedPart + unvoicedPart) * e;

    // Spectral tilt (dynamic lowpass)
    tiltFilter.setPole(0.97f - (e * 0.2f));
    src = tiltFilter.tick(src, t);

    float out = 0;
    double[] gains = FORMANT_GAINS[phonemeIdx];
    for (int i = 0; i < 4; i++) {
      out += filters[i].tick(src, t) * (float) gains[i];
    }

    lastOut = out * gain;
    return lastOut;
  }
}
