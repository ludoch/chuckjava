package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;

/** LiSaN: N-channel Live Sampling Utility. */
public class LiSaN extends MultiChannelUGen {
  private float[][] buffer;
  private int recPos = 0;
  private boolean isRecording = false;
  private final float sampleRate;

  private final Voice[] voices;

  public LiSaN(int numChannels, float sampleRate) {
    super(numChannels);
    this.sampleRate = sampleRate;
    this.buffer = new float[numChannels][(int) sampleRate]; // 1 second default
    this.voices = new Voice[10]; // Max 10 voices
    for (int i = 0; i < voices.length; i++) {
      voices[i] = new Voice();
    }
  }

  public void duration(long samples) {
    this.buffer = new float[lastOutChannels.length][(int) samples];
    this.recPos = 0;
  }

  public void record(int state) {
    this.isRecording = (state != 0);
  }

  public void play(int state) {
    voices[0].playing = (state != 0);
  }

  public void loop(int state) {
    voices[0].looping = (state != 0);
    if (state == 0) voices[0].dir = 1;
  }

  public void bi(int state) {
    voices[0].bidirectional = (state != 0);
  }

  public void play(int v, int state) {
    if (v >= 0 && v < 10) voices[v].playing = (state != 0);
  }

  public void pos(int v, long samples) {
    if (v >= 0 && v < 10) voices[v].playPos = samples;
  }

  public void rate(int v, float r) {
    if (v >= 0 && v < 10) voices[v].rate = r;
  }

  public void loop(int v, int state) {
    if (v >= 0 && v < 10) voices[v].looping = (state != 0);
  }

  @Override
  protected void computeMulti(float input, long systemTime) {
    int n = lastOutChannels.length;
    if (isRecording && buffer[0].length > 0) {
      // Get inputs from sources
      for (int c = 0; c < n; c++) {
        float in = 0;
        for (ChuckUGen src : getSources()) {
          in += src.getChannelLastOut(c);
        }
        buffer[c][recPos] = in;
      }
      recPos = (recPos + 1) % buffer[0].length;
    }

    // Clear outputs
    for (int c = 0; c < n; c++) lastOutChannels[c] = 0.0f;

    for (int i = 0; i < voices.length; i++) {
      Voice v = voices[i];
      if (!v.playing || buffer[0].length == 0) continue;

      int i0 = (int) v.playPos;
      int i1 = (i0 + 1) % buffer[0].length;
      float frac = (float) (v.playPos - i0);

      for (int c = 0; c < n; c++) {
        float s = buffer[c][i0] + (buffer[c][i1] - buffer[c][i0]) * frac;
        lastOutChannels[c] += s * v.gain;
      }

      // Advance play position
      v.playPos += v.rate * v.dir;
      if (v.playPos >= buffer[0].length) {
        if (v.bidirectional && v.looping) {
          v.playPos = Math.max(0, buffer[0].length - 1);
          v.dir = -1;
        } else if (v.looping) {
          v.playPos %= buffer[0].length;
        } else {
          v.playPos = buffer[0].length - 1;
          v.playing = false;
        }
      } else if (v.playPos < 0) {
        if (v.bidirectional && v.looping) {
          v.playPos = 0;
          v.dir = 1;
        } else if (v.looping) {
          v.playPos = buffer[0].length + (v.playPos % buffer[0].length);
        } else {
          v.playPos = 0;
          v.playing = false;
        }
      }
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
  }
}
