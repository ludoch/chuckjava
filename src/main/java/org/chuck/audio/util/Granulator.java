package org.chuck.audio.util;

import java.util.ArrayList;
import java.util.List;
import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/**
 * Granulator: Real-time granular synthesis engine. Captures input into a buffer and plays back
 * overlapping grains with randomized parameters.
 */
@doc("Real-time granular synthesis engine. Captures audio and generates randomized grains.")
public class Granulator extends ChuckUGen {
  private final float[] buffer;
  private int writePos = 0;
  private final float sampleRate;

  // Grain parameters
  private float grainSizeMs = 100.0f;
  private float grainSizeJitterMs = 0.0f;
  private float positionJitterMs = 0.0f;
  private float pitchJitter = 0.0f;
  private float density = 10.0f; // grains per second

  private final List<Grain> activeGrains = new ArrayList<>();
  private double samplesUntilNextGrain = 0;

  public Granulator(float sampleRate) {
    this.sampleRate = sampleRate;
    this.buffer = new float[(int) (sampleRate * 5)]; // 5 second buffer
    this.numOutputs = 2; // Stereo output
  }

  @doc("Set grain size in milliseconds.")
  public void grainSize(float ms) {
    this.grainSizeMs = Math.max(1.0f, ms);
  }

  @doc("Set randomization of grain size in ms.")
  public void grainSizeJitter(float ms) {
    this.grainSizeJitterMs = ms;
  }

  @doc("Set randomization of playback position in ms.")
  public void posJitter(float ms) {
    this.positionJitterMs = ms;
  }

  @doc("Set randomization of pitch (0.0 to 1.0).")
  public void pitchJitter(float j) {
    this.pitchJitter = j;
  }

  @doc("Set grain density (grains per second).")
  public void density(float d) {
    this.density = Math.max(0.1f, d);
  }

  @Override
  protected float compute(float input, long systemTime) {
    // 1. Record input into ring buffer
    buffer[writePos] = input;
    writePos = (writePos + 1) % buffer.length;

    // 2. Grain Spawning
    samplesUntilNextGrain--;
    if (samplesUntilNextGrain <= 0) {
      spawnGrain();
      samplesUntilNextGrain = sampleRate / density;
    }

    // 3. Render and update active grains
    float outL = 0, outR = 0;
    activeGrains.removeIf(g -> !g.active);

    for (Grain g : activeGrains) {
      float sample = g.tick();
      float p = (g.pan + 1.0f) / 2.0f;
      outL += sample * (1.0f - p);
      outR += sample * p;
    }

    lastOut = outL * gain;
    this.lastL = outL * gain;
    this.lastR = outR * gain;
    return lastOut;
  }

  private void spawnGrain() {
    float size = grainSizeMs + (float) (Math.random() * grainSizeJitterMs);
    int sizeSamples = (int) (size * sampleRate / 1000.0);
    if (sizeSamples < 10) return;

    // Position relative to current write head
    float posOffset = (float) (Math.random() * positionJitterMs * sampleRate / 1000.0);
    int startIdx = (writePos - sizeSamples - (int) posOffset + buffer.length) % buffer.length;

    float pitch = 1.0f + (float) ((Math.random() * 2.0 - 1.0) * pitchJitter);
    float pan = (float) (Math.random() * 2.0 - 1.0);

    activeGrains.add(new Grain(startIdx, sizeSamples, pitch, pan));
  }

  private float lastL = 0, lastR = 0;

  @Override
  public float getChannelLastOut(int i) {
    return (i == 0) ? lastL : lastR;
  }

  private class Grain {
    double playPos;
    int startPos;
    int duration;
    float rate;
    float pan;
    int age = 0;
    boolean active = true;

    Grain(int start, int duration, float rate, float pan) {
      this.startPos = start;
      this.playPos = start;
      this.duration = duration;
      this.rate = rate;
      this.pan = pan;
    }

    float tick() {
      if (age >= duration) {
        active = false;
        return 0;
      }

      // Linear interpolation
      int i0 = (int) playPos % buffer.length;
      int i1 = (i0 + 1) % buffer.length;
      float f = (float) (playPos - (int) playPos);
      float sample = buffer[i0] + (buffer[i1] - buffer[i0]) * f;

      // Hanning Window
      float win = (float) (0.5 * (1.0 - Math.cos(2.0 * Math.PI * age / (duration - 1))));

      playPos += rate;
      age++;
      return sample * win;
    }
  }
}
