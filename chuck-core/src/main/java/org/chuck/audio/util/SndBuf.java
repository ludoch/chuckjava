package org.chuck.audio.util;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.chuck.audio.ChuckUGen;
import org.chuck.audio.util.WavReader.WavData;

/**
 * A Unit Generator for sample playback. Loads a float array of samples and plays them back at a
 * given rate.
 */
public class SndBuf extends ChuckUGen {
  private static final Logger logger = Logger.getLogger(SndBuf.class.getName());
  private float[] samples;
  private double pos = 0.0;
  private double rate = 1.0;
  private boolean loop = false;

  @SuppressWarnings("unused")
  private float sampleRate = 44100.0f;

  public SndBuf() {
    this.samples = new float[0];
  }

  public SndBuf(float sampleRate) {
    this.samples = new float[0];
    this.sampleRate = sampleRate;
  }

  public void setSamples(float[] samples) {
    this.samples = samples;
    this.pos = 0;
  }

  public void setRate(double rate) {
    this.rate = rate;
  }

  public void setPos(double pos) {
    this.pos = pos;
  }

  public void setLoop(boolean loop) {
    this.loop = loop;
  }

  public void set(String path) {
    setRead(path);
  }

  private String currentPath = "";

  public void setRead(String path) {
    if (path == null || path.isEmpty()) {
      samples = new float[0];
      currentPath = "";
      return;
    }

    if (path.equals(currentPath)) return; // Avoid redundant loads
    currentPath = path;

    // Try loading as a real file
    try {
      java.io.File file = org.chuck.core.ChuckConfig.resolveFile(path);
      if (file != null && file.exists()) {
        if (tryLoadWav(file)) return;
        if (tryLoadAiff(file)) return;
        logger.log(Level.SEVERE, "[Audio] SndBuf: Unsupported format: " + path);
        samples = new float[0];
        return;
      }

      // Try resource fallback
      String resourcePath = path.replace("\\", "/");
      if (!resourcePath.startsWith("/")) resourcePath = "/" + resourcePath;

      java.io.InputStream ris = findResource(resourcePath);
      if (ris != null) {
        if (tryLoadWavStream(ris)) return;
      }

      logger.log(Level.SEVERE, "[Audio] SndBuf: File or resource not found: " + path);
      samples = new float[0];
    } catch (Exception e) {
      logger.log(
          Level.SEVERE, "[Audio] Error loading file '" + path + "': " + e.getMessage(), e);
      samples = new float[0];
    }
    pos = 0;
  }

  /** Try to load a WAV file. Returns true if successful. */
  private boolean tryLoadWav(java.io.File file) throws IOException {
    try {
      WavData wavData = WavReader.read(file);
      setSamplesFromWav(wavData, file.getPath());
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private boolean tryLoadWavStream(java.io.InputStream stream) throws IOException {
    try {
      WavData wavData = WavReader.read(new java.io.BufferedInputStream(stream));
      setSamplesFromWav(wavData, "(stream)");
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private void setSamplesFromWav(WavData wavData, String source) {
    int n = wavData.frameCount();
    samples = new float[n];
    for (int i = 0; i < n; i++) {
      samples[i] = (wavData.channels[0][i] + wavData.channels[1][i]) * 0.5f;
    }
    logger.log(Level.FINE, "[Audio] SndBuf: Loaded WAV " + source + " (" + samples.length + " samples)");
  }

  /** Try to load an AIFF/AIFF-C file. Returns true if successful. */
  private boolean tryLoadAiff(java.io.File file) throws IOException {
    try {
      AiffReader.AiffData aiffData = AiffReader.read(file);
      int n = aiffData.frameCount();
      samples = new float[n];
      for (int i = 0; i < n; i++) {
        samples[i] = (aiffData.channels[0][i] + aiffData.channels[1][i]) * 0.5f;
      }
      logger.log(Level.FINE, "[Audio] SndBuf: Loaded AIFF " + file.getPath() + " (" + samples.length + " samples)");
      return true;
    } catch (IOException e) {
      logger.log(Level.FINE, "[Audio] SndBuf: AIFF read failed for " + file.getPath() + ": " + e.getMessage());
      return false;
    }
  }

  private java.io.InputStream findResource(String path) {
    // 1. Direct match
    java.io.InputStream is = getResource(path);
    if (is != null) return is;

    // 2. Remove module prefix if any
    if (path.contains("-core/")) {
      is = getResource(path.substring(path.indexOf("-core/") + 5));
      if (is != null) return is;
    }
    if (path.contains("-samples/")) {
      is = getResource(path.substring(path.indexOf("-samples/") + 8));
      if (is != null) return is;
    }

    // 3. Try common locations
    if (path.contains("/data/")) {
      is = getResource("/examples/data/" + path.substring(path.lastIndexOf("/") + 1));
      if (is != null) return is;
    }

    return null;
  }

  private java.io.InputStream getResource(String path) {
    return SndBuf.class.getResourceAsStream(path);
  }

  public String read(String path) {
    setRead(path);
    return path;
  }

  public double pos(double p) {
    setPos(p);
    return p;
  }

  public long pos(long p) {
    setPos(p);
    return p;
  }

  public double rate(double r) {
    setRate(r);
    return r;
  }

  public float rate(float r) {
    setRate(r);
    return r;
  }

  public double rate() {
    return rate;
  }

  public long samples() {
    return samples.length;
  }

  public long length() {
    return samples.length;
  }

  public float valueAt(long index) {
    if (index < 0 || index >= samples.length) return 0.0f;
    return samples[(int) index];
  }

  public long pos() {
    return (long) pos;
  }

  public float db(float db) {
    this.gain = (float) Math.pow(10.0, db / 20.0);
    return db;
  }

  public float db() {
    return (float) (20.0 * Math.log10(this.gain));
  }

  @Override
  protected float compute(float input, long systemTime) {
    if (samples.length == 0) return 0.0f;

    if (pos >= samples.length || pos < 0) {
      if (loop && samples.length > 0) {
        pos = pos % samples.length;
        if (pos < 0) pos += samples.length;
      } else {
        return 0.0f;
      }
    }

    // Linear interpolation
    int i0 = (int) pos;
    int i1 = (i0 + 1) % samples.length;
    float frac = (float) (pos - i0);

    float s0 = samples[i0];
    float s1 = samples[i1];
    float out = s0 + (s1 - s0) * frac;

    // Click prevention: fade out in the last 1ms (approx 44 samples).
    // Only applies to arrays long enough that the fade zone is meaningful.
    if (!loop && rate > 0 && samples.length > 44 && pos > samples.length - 44) {
      double remaining = samples.length - 1 - pos;
      if (remaining < 44) {
        out *= (float) (remaining / 44.0);
      }
    }

    pos += rate;

    return out;
  }

  public boolean isDone() {
    return !loop && (pos >= samples.length || pos < 0);
  }

  public int ready() {
    return samples != null && samples.length > 0 ? 1 : 0;
  }
}
