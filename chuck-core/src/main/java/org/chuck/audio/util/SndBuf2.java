package org.chuck.audio.util;

import java.io.IOException;
import org.chuck.audio.util.WavReader.WavData;

/** SndBuf2: Stereo sample playback. Reads WAV using {@link WavReader} (no javax.sound). */
public class SndBuf2 extends StereoUGen {
  private float[][] samples; // [channel][sample]
  private double pos = 0.0;
  private double rate = 1.0;
  private boolean loop = false;
  private final float sampleRate;

  public SndBuf2(float sampleRate) {
    super();
    this.sampleRate = sampleRate;
    this.samples = new float[2][0];
  }

  public void setRead(String path) {
    try {
      java.io.File file = new java.io.File(path);
      if (!file.exists()) {
        samples = new float[2][0];
        return;
      }

      WavData wavData = WavReader.read(file);
      int n = wavData.frameCount();
      samples[0] = wavData.channels[0];
      samples[1] = wavData.channels[1];
    } catch (IOException e) {
      samples = new float[2][0];
    }
    pos = 0;
  }

  public void read(String path) {
    setRead(path);
  }

  public void rate(double r) {
    this.rate = r;
  }

  public void pos(double p) {
    this.pos = p;
  }

  public void loop(int l) {
    this.loop = (l != 0);
  }

  @Override
  protected void computeStereo(float input, long systemTime) {
    if (samples[0].length == 0 || pos >= samples[0].length || pos < 0) {
      if (loop && samples[0].length > 0) {
        pos = pos % samples[0].length;
        if (pos < 0) pos += samples[0].length;
      } else {
        lastOutChannels[0] = 0;
        lastOutChannels[1] = 0;
        return;
      }
    }

    int i0 = (int) pos;
    int i1 = (i0 + 1) % samples[0].length;
    float frac = (float) (pos - i0);

    for (int c = 0; c < 2; c++) {
      float s0 = samples[c][i0];
      float s1 = samples[c][i1];
      lastOutChannels[c] = s0 + (s1 - s0) * frac;
    }

    pos += rate;
  }
}
