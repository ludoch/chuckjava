package org.chuck.midi;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.ChuckFactory;
import org.chuck.core.ChuckShred;
import org.chuck.core.ChuckVM;

/**
 * MidiPoly: Automatic voice management for polyphonic MIDI instruments. Manages a pool of UGens and
 * maps MIDI Note messages to voices. Supports custom tuning maps.
 */
public class MidiPoly extends ChuckUGen {
  protected String instrumentType = "SinOsc";
  protected int numVoices = 8;
  protected Voice[] voicePool;
  protected int nextVoiceIdx = 0;
  protected float[] tuningMap = null; // Custom microtonal frequency mapping
  protected float velocityCurve = 1.0f; // 1.0 = linear, <1.0 = log, >1.0 = exp

  protected static class Voice {
    public ChuckUGen ugen;
    public int activeNote = -1; // -1 if idle
    public int activeChannel = -1; // MPE support
    public long startTime = 0;

    public Voice(ChuckUGen ugen) {
      this.ugen = ugen;
    }
  }

  public MidiPoly() {
    super();
    initPool();
  }

  /** ChucK API: set instrument class name (e.g. "Rhodey", "Mandolin") */
  public String setInstrument(String type) {
    this.instrumentType = type;
    initPool();
    return type;
  }

  public String instrument() {
    return instrumentType;
  }

  /** ChucK API: set max polyphony */
  public int voices(int n) {
    this.numVoices = Math.max(1, n);
    initPool();
    return numVoices;
  }

  public int voices() {
    return numVoices;
  }

  /** ChucK API: set custom microtonal tuning map (128 array of frequencies in Hz) */
  public void tuning(float[] freqs) {
    if (freqs != null && freqs.length >= 128) {
      this.tuningMap = freqs;
    }
  }

  /** ChucK API: reset to 12-TET tuning */
  public void standardTuning() {
    this.tuningMap = null;
  }

  /**
   * ChucK API: set velocity curve power (default 1.0). 1.0 = linear, 2.0 = exponential, 0.5 =
   * logarithmic.
   */
  public float curve(float c) {
    this.velocityCurve = Math.max(0.01f, c);
    return velocityCurve;
  }

  public float curve() {
    return velocityCurve;
  }

  protected void initPool() {
    ugenLock.lock();
    try {
      voicePool = new Voice[numVoices];
      ChuckVM vm = ChuckVM.CURRENT_VM.get();
      ChuckShred shred = ChuckShred.CURRENT_SHRED.get();

      for (int i = 0; i < numVoices; i++) {
        ChuckUGen u =
            (ChuckUGen)
                ChuckFactory.instantiateType(
                    instrumentType,
                    0,
                    null,
                    vm != null ? vm.getSampleRate() : 44100,
                    vm,
                    shred,
                    null);
        if (u == null)
          u = new org.chuck.audio.osc.SinOsc(vm != null ? (float) vm.getSampleRate() : 44100.0f);
        voicePool[i] = new Voice(u);
      }

    } finally {
      ugenLock.unlock();
    }
  }

  /** ChucK API: handle incoming MIDI message */
  public void onMessage(MidiMsg msg) {
    int status = msg.data1 & 0xF0;
    int note = msg.data2;
    int velocity = msg.data3;
    int channel = msg.data1 & 0x0F;

    if (status == 0x90 && velocity > 0) {
      float velNorm = velocity / 127.0f;
      if (velocityCurve != 1.0f) {
        velNorm = (float) Math.pow(velNorm, velocityCurve);
      }
      noteOn(note, velNorm, channel);
    } else if (status == 0x80 || (status == 0x90 && velocity == 0)) {
      noteOff(note, channel);
    }
  }

  protected void noteOn(int note, float velocity, int channel) {
    ugenLock.lock();
    try {
      // 1. Check if note already playing
      for (Voice v : voicePool) {
        if (v.activeNote == note) {
          triggerVoice(v, note, velocity, channel);
          return;
        }
      }

      // 2. Find idle voice
      for (Voice v : voicePool) {
        if (v.activeNote == -1) {
          triggerVoice(v, note, velocity, channel);
          return;
        }
      }

      // 3. Steal voice (round robin)
      Voice v = voicePool[nextVoiceIdx];
      nextVoiceIdx = (nextVoiceIdx + 1) % numVoices;
      triggerVoice(v, note, velocity, channel);

    } finally {
      ugenLock.unlock();
    }
  }

  protected void noteOff(int note, int channel) {
    ugenLock.lock();
    try {
      for (Voice v : voicePool) {
        if (v.activeNote == note) {
          v.ugen.next(0); // Standard UGen 'noteOff' trigger if available
          // Use reflection to call noteOff if it exists (e.g. STK)
          try {
            v.ugen.getClass().getMethod("noteOff", float.class).invoke(v.ugen, 1.0f);
          } catch (Exception ignored) {
          }

          v.activeNote = -1;
          v.activeChannel = -1;
        }
      }
    } finally {
      ugenLock.unlock();
    }
  }

  protected void triggerVoice(Voice v, int note, float velocity, int channel) {
    v.activeNote = note;
    v.activeChannel = channel;
    v.startTime = System.currentTimeMillis();

    // Map MIDI note to frequency
    double freq = 440.0 * Math.pow(2.0, (note - 69) / 12.0);
    if (tuningMap != null && note >= 0 && note < 128) {
      freq = tuningMap[note];
    }

    // Use reflection to call noteOn or freq/gain
    try {
      // Try STK-style noteOn(float freq, float gain)
      try {
        v.ugen
            .getClass()
            .getMethod("noteOn", float.class, float.class)
            .invoke(v.ugen, (float) freq, velocity);
      } catch (NoSuchMethodException e) {
        // Fallback to basic UGen props
        v.ugen.getClass().getMethod("setFreq", float.class).invoke(v.ugen, (float) freq);
        v.ugen.getClass().getMethod("setGain", float.class).invoke(v.ugen, velocity);
      }
    } catch (Exception ignored) {
    }
  }

  @Override
  protected float compute(float input, long systemTime) {
    float sum = 0;
    Voice[] activePool = voicePool;
    if (activePool == null) return 0;

    for (Voice v : activePool) {
      sum += v.ugen.tick(systemTime);
    }
    return sum;
  }
}
