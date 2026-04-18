package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;

/**
 * LiSa: Live Sampling Utility. Provides multi-voice, multi-channel live sampling with cubic
 * interpolation.
 */
public class LiSa extends ChuckUGen {
  private float[] buffer;
  private int recPos = 0;
  private boolean isRecording = false;
  private float feedback = 0.0f;

  private final float sampleRate;
  private final Voice[] voices;
  private static final int MAX_VOICES = 256;

  public LiSa(float sampleRate) {
    this.sampleRate = sampleRate;
    this.buffer = new float[(int) sampleRate]; // 1 second default
    this.voices = new Voice[MAX_VOICES];
    for (int i = 0; i < voices.length; i++) {
      voices[i] = new Voice();
    }
    this.numOutputs = 2; // Default to stereo output
  }

  public void duration(long samples) {
    this.buffer = new float[(int) samples];
    this.recPos = 0;
  }

  public void duration(org.chuck.core.ChuckDuration dur) {
    duration((long) dur.samples());
  }

  public void record(int state) {
    this.isRecording = (state != 0);
  }

  public void recPos(long samples) {
    if (buffer.length > 0) this.recPos = (int) (samples % buffer.length);
  }

  public void feedback(float f) {
    this.feedback = f;
  }

  // --- Voice Management ---

  public void play(int v, int state) {
    if (v >= 0 && v < MAX_VOICES) voices[v].playing = (state != 0);
  }

  public void pos(int v, double samples) {
    if (v >= 0 && v < MAX_VOICES) voices[v].playPos = samples;
  }

  public void rate(int v, float r) {
    if (v >= 0 && v < MAX_VOICES) voices[v].rate = r;
  }

  public void loop(int v, int state) {
    if (v >= 0 && v < MAX_VOICES) voices[v].looping = (state != 0);
  }

  public void bi(int v, int state) {
    if (v >= 0 && v < MAX_VOICES) voices[v].bidirectional = (state != 0);
  }

  public void voiceGain(int v, float g) {
    if (v >= 0 && v < MAX_VOICES) voices[v].gain = g;
  }

  public void voicePan(int v, float p) {
    if (v >= 0 && v < MAX_VOICES) voices[v].pan = p;
  }

  /** ChucK-style convenience for voice 0 */
  public void play(int state) {
    play(0, state);
  }

  public void loop(int state) {
    loop(0, state);
  }

  public void bi(int state) {
    bi(0, state);
  }

  public void rate(float r) {
    rate(0, r);
  }

  public void pos(double p) {
    pos(0, p);
  }

  @Override
  protected float compute(float input, long systemTime) {
    // Recording with optional feedback
    if (isRecording && buffer.length > 0) {
      buffer[recPos] = input + (buffer[recPos] * feedback);
      recPos = (recPos + 1) % buffer.length;
    }

    float outL = 0.0f;
    float outR = 0.0f;

    for (Voice v : voices) {
      if (!v.playing || buffer.length < 4) continue;

      // Cubic Hermite Spline Interpolation
      double pos = v.playPos;
      int i = (int) pos;
      float f = (float) (pos - i);

      int i0 = (i - 1 + buffer.length) % buffer.length;
      int i1 = i % buffer.length;
      int i2 = (i + 1) % buffer.length;
      int i3 = (i + 2) % buffer.length;

      float y0 = buffer[i0], y1 = buffer[i1], y2 = buffer[i2], y3 = buffer[i3];
      float a = (3 * (y1 - y2) - y0 + y3) / 2.0f;
      float b = 2 * y2 + y0 - 5 * y1 / 2.0f - y3 / 2.0f;
      float c = (y2 - y0) / 2.0f;
      float s = ((a * f + b) * f + c) * f + y1;

      float voiceOut = s * v.gain;

      // Basic Panning
      float p = (v.pan + 1.0f) / 2.0f; // [-1, 1] -> [0, 1]
      outL += voiceOut * (1.0f - p);
      outR += voiceOut * p;

      // Advance play position
      v.playPos += v.rate * v.dir;

      // Looping / Edge Logic
      if (v.playPos >= buffer.length) {
        if (v.bidirectional && v.looping) {
          v.playPos = buffer.length - 1;
          v.dir = -1;
        } else if (v.looping) {
          v.playPos %= buffer.length;
        } else {
          v.playing = false;
        }
      } else if (v.playPos < 0) {
        if (v.bidirectional && v.looping) {
          v.playPos = 0;
          v.dir = 1;
        } else if (v.looping) {
          v.playPos = buffer.length + (v.playPos % buffer.length);
        } else {
          v.playing = false;
        }
      }
    }

    // Set lastOut for multi-channel support
    this.lastL = outL;
    this.lastR = outR;
    this.lastOut = outL;
    return outL;
  }

  @Override
  public float getChannelLastOut(int i) {
    // If we're stereo, we can actually store both.
    // For now, let's just use 0 for L, 1 for R.
    // We need to store these in the compute pass.
    return (i == 0) ? lastL : lastR;
  }

  private float lastL = 0, lastR = 0;

  @Override
  public void tick(float[] buffer, int offset, int length, long systemTime) {
    // Standard implementation for now
    for (int i = 0; i < length; i++) {
      float in = (sources.isEmpty()) ? 0.0f : sources.get(0).lastOut; // simplify
      buffer[offset + i] = compute(in, systemTime == -1 ? -1 : systemTime + i);
      // Update lastL/lastR in compute or here
    }
  }

  private static class Voice {
    double playPos = 0;
    float rate = 1.0f;
    int dir = 1;
    boolean playing = false;
    boolean looping = false;
    boolean bidirectional = false;
    float gain = 1.0f;
    float pan = 0.0f; // [-1, 1]
  }
}
