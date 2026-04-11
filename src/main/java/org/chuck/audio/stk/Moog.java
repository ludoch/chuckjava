package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.filter.Lpf;
import org.chuck.audio.osc.SinOsc;
import org.chuck.audio.util.Adsr;

/** Moog: STK Moog-style synthesizer model. Uses a resonant low-pass filter and ADSR envelope. */
public class Moog extends ChuckUGen {
  private final SinOsc[] oscillators = new SinOsc[2];
  private final Lpf filter;
  private final Adsr adsr;

  @SuppressWarnings("unused")
  private float filterQ = 0.5f;

  private float filterSweep = 0.0f;
  private double freq = 440.0;

  @SuppressWarnings("unused")
  private float sampleRate;

  public Moog(float sampleRate) {
    this.sampleRate = sampleRate;
    oscillators[0] = new SinOsc(sampleRate);
    oscillators[1] = new SinOsc(sampleRate);
    filter = new Lpf(sampleRate);
    adsr = new Adsr(sampleRate);
    adsr.set(0.01f, 0.1f, 0.5f, 0.2f);
    setFreq(440.0);
  }

  public void setFreq(double f) {
    this.freq = f;
    oscillators[0].setFreq(f);
    oscillators[1].setFreq(f * 1.005); // Slight detune
    filter.setCutoff((float) (f * (1.0 + filterSweep)));
  }

  public void noteOn(float velocity) {
    adsr.keyOn();
  }

  public void noteOff(float velocity) {
    adsr.keyOff();
  }

  public void filterQ(float q) {
    this.filterQ = q;
  }

  public void filterSweep(float s) {
    this.filterSweep = s;
  }

  @Override
  protected float compute(float input, long systemTime) {
    float sum =
        (oscillators[0].tick(systemTime, systemTime) + oscillators[1].tick(systemTime, systemTime))
            * 0.5f;
    float env = adsr.tick(systemTime, systemTime);

    // Dynamic filter tracking
    filter.setCutoff((float) (freq * (1.0 + filterSweep * env)));

    lastOut = filter.tick(sum, systemTime) * env;
    return lastOut;
  }
}
