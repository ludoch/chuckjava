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

  private String currentPath = "";

  public void setRead(String path) {
    if (path == null || path.isEmpty()) {
      samples = new float[2][0];
      currentPath = "";
      return;
    }

    if (path.equals(currentPath)) return; // Avoid redundant loads
    currentPath = path;

    if (path.toLowerCase().startsWith("special:")) {
      // Delegate to SndBuf to synthesize the special mono wave and duplicate to both stereo
      // channels
      SndBuf helper = new SndBuf(sampleRate);
      helper.setRead(path);
      float[] mono = new float[helper.samples() > 0 ? (int) helper.samples() : 0];
      for (int i = 0; i < mono.length; i++) {
        helper.setPos(i);
        mono[i] = helper.compute(0.0f, 0L);
      }
      samples[0] = mono;
      samples[1] = mono.clone();
      pos = 0;
      return;
    }

    try {
      java.io.File file = org.chuck.core.ChuckConfig.resolveFile(path);
      if (file != null && file.exists()) {
        WavData wavData = WavReader.read(file);
        samples[0] = wavData.channels[0];
        samples[1] = wavData.channels.length > 1 ? wavData.channels[1] : wavData.channels[0];
        pos = 0;
        return;
      }

      // Resource fallback
      String resourcePath = path.replace("\\", "/");
      if (!resourcePath.startsWith("/")) resourcePath = "/" + resourcePath;
      java.io.InputStream ris = SndBuf.class.getResourceAsStream(resourcePath);
      if (ris != null) {
        WavData wavData = WavReader.read(ris);
        samples[0] = wavData.channels[0];
        samples[1] = wavData.channels.length > 1 ? wavData.channels[1] : wavData.channels[0];
        pos = 0;
        return;
      }

      samples = new float[2][0];
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
