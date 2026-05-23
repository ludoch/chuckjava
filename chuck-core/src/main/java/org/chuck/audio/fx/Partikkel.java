package org.chuck.audio.fx;

import java.util.ArrayList;
import java.util.List;
import org.chuck.audio.util.StereoUGen;
import org.chuck.core.doc;

/**
 * Partikkel: Premium multi-parameter overlapping granular synthesis pool. Ported from Csound's
 * legendary partikkel opcode (Oeyvind Brandtsegg / Thom Johansen).
 *
 * <p>Maintains a fixed, pre-allocated pool of 128 dynamic grains to generate rich high-density
 * cloud soundscapes, micro-event structures, and organic physical timbres.
 */
@doc("Premium multi-parameter overlapping granular synthesis pool.")
public class Partikkel extends StereoUGen {

  private static final double DEFAULT_SRATE = 44100.0;

  // Active individual grain tracking
  private static class Grain {
    boolean active = false;
    float[] waveTable;
    double phase = 0.0;
    double phaseInc = 0.0;

    int currentAge = 0;
    int totalSamples = 0;

    float gain = 1.0f;
    float panLeft = 0.5f;
    float panRight = 0.5f;

    double envAttackSamples = 0.0;
    double envDecayStartSamples = 0.0;
  }

  // Pre-allocated pool of grains to prevent GC jitter
  private final Grain[] grainPool = new Grain[128];
  private final List<Grain> activeGrains = new ArrayList<>();

  // Up to 4 distinct source waveform tables
  private final float[][] waveTables = new float[4][];

  // Parameters
  private float grainFreq = 10.0f; // Grain scheduler rate (Hz)
  private float distribution = 0.0f; // Timing random time jitter factor
  private float duration = 100.0f; // Grain duration in milliseconds
  private float amplitude = 0.8f; // Master granular output volume
  private float wavFreq = 1.0f; // Master sound pitch playback speed scaler
  private float mix = 1.0f; // Dry/Wet mix (defaults to 100% wet!)

  // Multi-slot configuration arrays
  private final float[] waveKeys = {1.0f, 1.0f, 1.0f, 1.0f}; // Slot pitch ratios
  private final float[] waveAmps = {1.0f, 0.0f, 0.0f, 0.0f}; // Slot volume mixes
  private final float[] samplePositions = {0.0f, 0.0f, 0.0f, 0.0f}; // Slot seek offsets (0.0-1.0)

  // Grain envelope proportions
  private float attackRatio = 0.1f; // Attack phase proportion (0.0-1.0)
  private float sustainRatio = 0.8f; // Sustain phase proportion (0.0-1.0)

  private double grainPhase = 0.0;
  private double sampleRate = 44100.0;
  private int currentSeed = 12345;

  public Partikkel() {
    super();
    this.sampleRate =
        org.chuck.core.ChuckVM.CURRENT_VM.isBound()
            ? org.chuck.core.ChuckVM.CURRENT_VM.get().getSampleRate()
            : DEFAULT_SRATE;

    // Allocate grain objects once on boot
    for (int i = 0; i < grainPool.length; i++) {
      grainPool[i] = new Grain();
    }

    // Load a default single-cycle sine wave in slot 0 if no buffer is loaded yet
    float[] defaultSine = new float[1024];
    for (int i = 0; i < 1024; i++) {
      defaultSine[i] = (float) Math.sin(2.0 * Math.PI * i / 1024.0);
    }
    waveTables[0] = defaultSine;
  }

  @doc("Load a floating-point sample waveform buffer into a target slot (0 to 3).")
  public void waveform(int slot, float[] table) {
    if (slot >= 0 && slot < 4) {
      this.waveTables[slot] = table;
    }
  }

  @doc("Set grain scheduler trigger rate (Hz).")
  public void grainFreq(float f) {
    this.grainFreq = Math.max(0.1f, f);
  }

  public float grainFreq() {
    return grainFreq;
  }

  @doc("Set timing stochastic jitter variance multiplier (0.0 to 1.0).")
  public void distribution(float dist) {
    this.distribution = Math.max(0.0f, Math.min(1.0f, dist));
  }

  public float distribution() {
    return distribution;
  }

  @doc("Set target grain duration in milliseconds.")
  public void duration(float ms) {
    this.duration = Math.max(1.0f, ms);
  }

  public float duration() {
    return duration;
  }

  @doc("Set master grain pitch speed scaler factor (e.g. 1.0 = normal, 2.0 = double pitch).")
  public void wavFreq(float f) {
    this.wavFreq = Math.max(0.0f, f);
  }

  public float wavFreq() {
    return wavFreq;
  }

  @doc("Set pitch transposition key ratio multiplier for a target slot (0 to 3).")
  public void waveKey(int slot, float multiplier) {
    if (slot >= 0 && slot < 4) {
      this.waveKeys[slot] = Math.max(0.0f, multiplier);
    }
  }

  public float waveKey(int slot) {
    return slot >= 0 && slot < 4 ? waveKeys[slot] : 1.0f;
  }

  @doc("Set volume mix ratio for a target slot (0 to 3).")
  public void waveAmp(int slot, float amp) {
    if (slot >= 0 && slot < 4) {
      this.waveAmps[slot] = Math.max(0.0f, amp);
    }
  }

  public float waveAmp(int slot) {
    return slot >= 0 && slot < 4 ? waveAmps[slot] : 0.0f;
  }

  @doc("Set starting playback position seek offset (0.0 to 1.0) for a target slot (0 to 3).")
  public void samplePos(int slot, float pos) {
    if (slot >= 0 && slot < 4) {
      this.samplePositions[slot] = Math.max(0.0f, Math.min(1.0f, pos));
    }
  }

  public float samplePos(int slot) {
    return slot >= 0 && slot < 4 ? samplePositions[slot] : 0.0f;
  }

  @doc("Set grain envelope attack and sustain ratio proportions (0.0 to 1.0).")
  public void envelope(float attack, float sustain) {
    this.attackRatio = Math.max(0.0f, Math.min(1.0f, attack));
    this.sustainRatio = Math.max(0.0f, Math.min(1.0f - attackRatio, sustain));
  }

  @doc("Set dry/wet output channels mix (0.0 to 1.0).")
  public void mix(float m) {
    this.mix = Math.max(0.0f, Math.min(1.0f, m));
  }

  public float mix() {
    return mix;
  }

  private int nextRand() {
    long temp = (long) currentSeed * 16807L;
    currentSeed = (int) (temp % 2147483647L);
    return currentSeed;
  }

  private double getJitter() {
    int rnd = nextRand();
    return (double) rnd / 2147483647.0 * distribution;
  }

  private void spawnGrain(int slot) {
    float[] table = waveTables[slot];
    if (table == null || table.length == 0) return;

    // 1. Retrieve first idle grain from the pool
    Grain grain = null;
    for (Grain g : grainPool) {
      if (!g.active) {
        grain = g;
        break;
      }
    }

    // If pool is full, recycle the oldest active grain
    if (grain == null && !activeGrains.isEmpty()) {
      grain = activeGrains.remove(0);
      grain.active = false;
    }

    if (grain == null) return;

    // 2. Populate grain details
    grain.waveTable = table;
    grain.currentAge = 0;

    double durSamples = (duration / 1000.0) * sampleRate;
    grain.totalSamples = (int) (durSamples + 0.5);
    if (grain.totalSamples < 2) return;

    // Linear envelope bounds
    grain.envAttackSamples = durSamples * attackRatio;
    grain.envDecayStartSamples = durSamples * (attackRatio + sustainRatio);

    // Dynamic pitch and start phase pointer seek
    double pitchScale = wavFreq * waveKeys[slot];
    grain.phaseInc = pitchScale;
    grain.phase = samplePositions[slot] * (table.length - 1);

    // Volume gain and dynamic surround pan width offsets
    grain.gain = waveAmps[slot] * amplitude;

    int rndPan = nextRand();
    double pan = (double) rndPan / 2147483647.0;
    grain.panLeft = (float) Math.cos(pan * Math.PI * 0.5);
    grain.panRight = (float) Math.sin(pan * Math.PI * 0.5);

    grain.active = true;
    activeGrains.add(grain);
  }

  @Override
  protected void computeStereo(float left, float right, long systemTime) {
    // 1. Advance Scheduler
    double stepSize = grainFreq / sampleRate;
    grainPhase += stepSize + getJitter() * stepSize;
    if (grainPhase >= 1.0) {
      // Spawn grains for active waveform tables (split evenly across slot mixes!)
      for (int slot = 0; slot < 4; slot++) {
        if (waveAmps[slot] > 0.0f && waveTables[slot] != null) {
          spawnGrain(slot);
        }
      }
      grainPhase %= 1.0;
    }

    // 2. Accumulate active grains outputs
    double wetL = 0.0;
    double wetR = 0.0;

    for (int i = activeGrains.size() - 1; i >= 0; i--) {
      Grain g = activeGrains.get(i);

      // Deactivate expired grains
      if (g.currentAge >= g.totalSamples) {
        g.active = false;
        activeGrains.remove(i);
        continue;
      }

      // Linear envelope state amplitude
      double env = 1.0;
      if (g.currentAge < g.envAttackSamples) {
        env = g.currentAge / g.envAttackSamples;
      } else if (g.currentAge >= g.envDecayStartSamples) {
        env = (double) (g.totalSamples - g.currentAge) / (g.totalSamples - g.envDecayStartSamples);
      }
      env = Math.max(0.0, Math.min(1.0, env));

      // Read sample with linear phase interpolation
      int p1 = (int) g.phase;
      int p2 = (p1 + 1) % g.waveTable.length;
      double frac = g.phase - p1;

      float s1 = g.waveTable[p1];
      float s2 = g.waveTable[p2];
      double grainSample = s1 + frac * (s2 - s1);

      // Apply master gain, spatial pan splits, and envelopes
      double outputVal = grainSample * g.gain * env;
      wetL += outputVal * g.panLeft;
      wetR += outputVal * g.panRight;

      // Increment grain age and phase pointers
      g.phase = (g.phase + g.phaseInc) % g.waveTable.length;
      g.currentAge++;
    }

    // 3. Dry/Wet blend
    lastOutChannels[0] = (float) (left * (1.0f - mix) + wetL * mix);
    lastOutChannels[1] = (float) (right * (1.0f - mix) + wetR * mix);
  }

  @Override
  protected void computeStereo(float input, long systemTime) {
    computeStereo(input, input, systemTime);
  }
}
