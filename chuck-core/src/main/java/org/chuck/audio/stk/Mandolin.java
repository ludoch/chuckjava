package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.stk.util.StkDelayA;
import org.chuck.audio.stk.util.StkDelayL;
import org.chuck.audio.stk.util.StkOneZero;
import org.chuck.audio.stk.util.StkWvIn;
import org.chuck.audio.stk.util.WaveData;
import org.chuck.core.doc;

/**
 * Mandolin — STK's commuted dual-string mandolin (PluckTwo + Mandolin), ported verbatim from
 * ugen_stk.cpp. Two detuned allpass-interpolating delay lines with one-zero loop filters, excited by
 * the commuted body response (special:mand1) shaped by a comb delay. Mono output, matching native
 * ChucK. Replaces the earlier Twang-based approximation.
 */
@doc("Mandolin physical model: two detuned waveguide strings with commuted body excitation.")
public class Mandolin extends ChuckUGen {
  private final double sampleRate;

  // ── PluckTwo state ────────────────────────────────────────────────────────
  private final int length;
  private double baseLoopGain = 0.995;
  private double loopGain = 0.999;
  private final StkDelayA delayLine;
  private final StkDelayA delayLine2;
  private final StkDelayL combDelay;
  private final StkOneZero filter = new StkOneZero();
  private final StkOneZero filter2 = new StkOneZero();
  private double pluckAmplitude = 0.3;
  private double pluckPosition = 0.4;
  private double detuning = 0.995;
  private double lastFrequency;
  private double lastLength;

  // ── Mandolin state ────────────────────────────────────────────────────────
  private final StkWvIn soundfile;
  private long dampTime = 0;
  private boolean waveDone = true;

  public Mandolin(float lowestFrequency, float sampleRate) {
    this.sampleRate = sampleRate;
    length = (int) (sampleRate / lowestFrequency + 1);
    delayLine = new StkDelayA(length / 2.0, length);
    delayLine2 = new StkDelayA(length / 2.0, length);
    combDelay = new StkDelayL(length / 2.0, length);
    lastFrequency = lowestFrequency * 2.0;
    lastLength = length * 0.5;

    soundfile = new StkWvIn(WaveData.MAND1, sampleRate);
    waveDone = true;
  }

  // ── PluckTwo ──────────────────────────────────────────────────────────────

  public void setFrequency(double frequency) {
    lastFrequency = (frequency <= 0.0) ? 220.0 : frequency;
    lastLength = (sampleRate / lastFrequency) - 1; // REPAIRATHON2021 bug-fix (minus 1)

    double delay = (lastLength / detuning) - 0.5;
    if (delay <= 0.0) delay = 0.3;
    else if (delay > length) delay = length;
    delayLine.setDelay(delay);

    delay = (lastLength * detuning) - 0.5;
    if (delay <= 0.0) delay = 0.3;
    else if (delay > length) delay = length;
    delayLine2.setDelay(delay);

    loopGain = baseLoopGain + (frequency * 0.000005);
    if (loopGain > 1.0) loopGain = 0.99999;
  }

  public void setDetune(double detune) {
    detuning = (detune <= 0.0) ? 0.1 : detune;
    delayLine.setDelay((lastLength / detuning) - 0.5);
    delayLine2.setDelay((lastLength * detuning) - 0.5);
  }

  public void setPluckPosition(double position) {
    pluckPosition = Math.max(0.0, Math.min(1.0, position));
  }

  public void setBaseLoopGain(double aGain) {
    baseLoopGain = aGain;
    loopGain = baseLoopGain + (lastFrequency * 0.000005);
    if (loopGain > 0.99999) loopGain = 0.99999;
  }

  // ── Mandolin ──────────────────────────────────────────────────────────────

  public void pluckString(double amplitude) {
    soundfile.reset();
    waveDone = false;
    pluckAmplitude = Math.max(0.0, Math.min(1.0, amplitude));
    combDelay.setDelay(0.5 * pluckPosition * lastLength);
    dampTime = (long) lastLength;
  }

  // ── ChucK control surface (preserved names) ────────────────────────────────

  @doc("Set the fundamental frequency of the mandolin.")
  public void freq(double f) {
    setFrequency(f);
  }

  public void setFreq(double f) {
    setFrequency(f);
  }

  @doc("Set the detuning factor between the two strings (e.g. 0.995).")
  public void detune(double d) {
    setDetune(d);
  }

  @doc("Set the pluck position (0.0 to 1.0).")
  public void pluckPos(double p) {
    setPluckPosition(p);
  }

  @doc("Set the string sustain (base loop gain, 0.0 to 1.0).")
  public void sustain(double g) {
    setBaseLoopGain(g);
  }

  @doc("Pluck the strings with given amplitude.")
  public void pluck(float amplitude) {
    pluckString(amplitude);
  }

  public void noteOn(float amplitude) {
    pluckString(amplitude);
  }

  public void noteOff(float amplitude) {
    loopGain = (1.0 - amplitude) * 0.5; // PluckTwo::noteOff
  }

  @Override
  protected float compute(float input, long systemTime) {
    double temp = 0.0;
    if (!waveDone) {
      // Comb-filtered pluck excitation for the duration of the body response.
      temp = soundfile.tick() * pluckAmplitude;
      temp = temp - combDelay.tick(temp);
      waveDone = soundfile.isFinished();
    }

    if (dampTime >= 0) {
      // Damping hack to avoid overflow on re-plucking (first period uses 0.7 reflection).
      dampTime -= 1;
      double out = delayLine.tick(filter.tick(temp + (delayLine.lastOut() * 0.7)));
      out += delayLine2.tick(filter2.tick(temp + (delayLine2.lastOut() * 0.7)));
      out *= 0.3;
      return (float) out * gain;
    } else {
      double out = delayLine.tick(filter.tick(temp + (delayLine.lastOut() * loopGain)));
      out += delayLine2.tick(filter2.tick(temp + (delayLine2.lastOut() * loopGain)));
      out *= 0.3;
      return (float) out * gain;
    }
  }
}
