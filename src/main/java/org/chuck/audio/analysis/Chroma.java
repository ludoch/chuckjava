package org.chuck.audio.analysis;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.UAna;

/**
 * Chroma — 12-bin pitch-class profile (chromagram) UAna. Chains after FFT: maps FFT magnitude bins
 * to the 12 semitone pitch classes. Useful for key/chord detection.
 *
 * <p>Output: 12 fvals corresponding to [C, C#, D, D#, E, F, F#, G, G#, A, A#, B].
 *
 * <p>Usage in ChucK: adc => FFT fft => Chroma chroma => blackhole; fft.size(2048); while (true) {
 * 2048::samp => now; chroma.upchuck() @=> UAnaBlob blob; }
 */
public class Chroma extends UAna {
  private float sampleRate;

  public Chroma() {
    this(44100f);
  }

  public Chroma(float sr) {
    this.sampleRate = sr;
  }

  public void setSampleRate(float sr) {
    this.sampleRate = sr;
  }

  @Override
  protected float compute(float input, long systemTime) {
    return input;
  }

  @Override
  protected void computeUAna() {
    // Pull magnitude spectrum from upstream FFT/UAna
    float[] mag = null;
    int fftSize = 2048;
    for (ChuckUGen src : sources) {
      if (src instanceof FFT fft) {
        fftSize = fft.getSize();
        mag = fft.lastBlob.getFvals();
        break;
      } else if (src instanceof UAna u) {
        mag = u.lastBlob.getFvals();
        break;
      }
    }
    if (mag == null || mag.length == 0) {
      lastBlob.setFvals(new float[12]);
      return;
    }

    float[] chroma = new float[12];
    int bins = mag.length;
    double binHz = sampleRate / (2.0 * bins); // Hz per bin

    for (int i = 1; i < bins; i++) {
      double freq = i * binHz;
      if (freq < 20.0 || freq > 20000.0) continue;
      // Convert frequency to MIDI note (A4=440Hz = MIDI 69)
      double midi = 12.0 * Math.log(freq / 440.0) / Math.log(2.0) + 69.0;
      int pitchClass = ((int) Math.round(midi) % 12 + 12) % 12; // 0=C, 1=C#, ...
      chroma[pitchClass] += mag[i];
    }

    // Normalize so the maximum is 1.0
    float maxVal = 0;
    for (float v : chroma) if (v > maxVal) maxVal = v;
    if (maxVal > 1e-10f) {
      for (int i = 0; i < 12; i++) chroma[i] /= maxVal;
    }

    lastBlob.setFvals(chroma);
  }
}
