package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.util.Wavetable;
import org.chuck.audio.util.WavetableRegistry;
import org.chuck.core.doc;

/**
 * Mandolin physical model. Based on STK Mandolin class. Uses two detuned Twang strings and a pluck
 * excitation.
 */
@doc("Mandolin physical model with two detuned strings and pluck excitation.")
public class Mandolin extends ChuckUGen {
  private final Twang[] strings = new Twang[2];
  private final Wavetable excitation;
  private double freq = 440.0;
  private double detuning = 0.995;
  private final float sampleRate;

  public Mandolin(float lowestFrequency, float sampleRate) {
    this.sampleRate = sampleRate;
    strings[0] = new Twang(sampleRate);
    strings[1] = new Twang(sampleRate);

    excitation = new Wavetable();
    excitation.setTable(WavetableRegistry.getPluckExcitation(1024));
    excitation.loop(0); // single shot pluck

    // Default mandolin parameters
    for (Twang s : strings) {
      s.loopGain(0.997);
      s.pluckPos(0.4);
    }

    setFreq(440.0);
    this.numOutputs = 2; // Stereo output
  }

  @doc("Set the fundamental frequency of the mandolin.")
  public void freq(double f) {
    this.freq = f;
    strings[0].freq(f);
    strings[1].freq(f * detuning);
  }

  public void setFreq(double f) {
    freq(f);
  }

  @doc("Set the detuning factor between the two strings (e.g. 0.995).")
  public void detune(double d) {
    this.detuning = d;
    strings[1].freq(freq * detuning);
  }

  @doc("Set the pluck position (0.0 to 1.0).")
  public void pluckPos(double p) {
    for (Twang s : strings) s.pluckPos(p);
  }

  @doc("Set the string sustain (loop gain, 0.0 to 1.0).")
  public void sustain(double g) {
    for (Twang s : strings) s.loopGain(g);
  }

  @doc("Pluck the strings with given velocity.")
  public void noteOn(float velocity) {
    excitation.reset();
    excitation.gain(velocity);
  }

  public void pluck(float velocity) {
    noteOn(velocity);
  }

  public void noteOff(float velocity) {
    sustain(0.9); // dampen strings
  }

  @Override
  public void tick(float[] buffer, int offset, int length, long systemTime) {
    if (systemTime != -1
        && systemTime == blockStartTime
        && blockCache != null
        && blockLength >= length) {
      if (buffer != null) System.arraycopy(blockCache, 0, buffer, offset, length);
      return;
    }

    if (blockCache == null || blockCache.length < length) {
      blockCache = new float[length];
    }

    // 1. Tick excitation wavetable
    float[] pluckBuf = new float[length];
    excitation.tick(pluckBuf, 0, length, systemTime);

    // 2. Tick strings using the excitation buffer
    float[] temp0 = new float[length];
    float[] temp1 = new float[length];
    strings[0].tick(temp0, 0, length, systemTime, pluckBuf);
    strings[1].tick(temp1, 0, length, systemTime, pluckBuf);

    // 3. Mix and store
    for (int i = 0; i < length; i++) {
      float out = (temp0[i] + temp1[i]) * 0.5f * gain;
      blockCache[i] = out;
      if (buffer != null) buffer[offset + i] = out;
    }

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = (systemTime == -1) ? -1 : systemTime + length - 1;
    if (length > 0) {
      this.lastL = temp0[length - 1] * gain;
      this.lastR = temp1[length - 1] * gain;
      lastOut = blockCache[length - 1];
    }
  }

  @Override
  protected float compute(float input, long systemTime) {
    float pluckSignal = excitation.tick(systemTime);

    // Explicitly pass pluck signal to internal strings
    float out0 = strings[0].tick(pluckSignal, systemTime);
    float out1 = strings[1].tick(pluckSignal, systemTime);

    // Update stereo cache
    this.lastL = out0 * gain;
    this.lastR = out1 * gain;

    // Mixed mono out (multiplied by gain in parent tick)
    return (out0 + out1) * 0.5f;
  }

  private float lastL = 0, lastR = 0;

  @Override
  public float getChannelLastOut(int i) {
    return (i == 0) ? lastL : lastR;
  }
}
