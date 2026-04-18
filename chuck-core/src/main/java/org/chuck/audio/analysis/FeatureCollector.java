package org.chuck.audio.analysis;

import java.util.ArrayList;
import java.util.List;
import org.chuck.audio.ChuckUGen;
import org.chuck.audio.UAna;

/**
 * FeatureCollector — aggregates fvals from all connected UAna sources into a single concatenated
 * feature vector. This is the bridge between the UAna analysis graph and the AI/ML classes (KNN,
 * MLP, etc.).
 *
 * <p>Usage in ChucK: adc => FFT fft => blackhole; fft => MFCC mfcc => FeatureCollector fc =>
 * blackhole; fft => Chroma chroma => fc; while (true) { 2048::samp => now; fc.upchuck() @=>
 * UAnaBlob blob; // blob.fvals() now contains [mfcc coefficients..., chroma bins...] }
 */
public class FeatureCollector extends UAna {
  @Override
  protected float compute(float input, long systemTime) {
    return input;
  }

  @Override
  protected void computeUAna() {
    List<float[]> parts = new ArrayList<>();
    int total = 0;
    for (ChuckUGen src : getSources()) {
      if (src instanceof UAna u) {
        float[] fv = u.lastBlob.getFvals();
        if (fv != null && fv.length > 0) {
          parts.add(fv);
          total += fv.length;
        }
      }
    }
    float[] combined = new float[total];
    int idx = 0;
    for (float[] part : parts) {
      System.arraycopy(part, 0, combined, idx, part.length);
      idx += part.length;
    }
    lastBlob.setFvals(combined);
  }
}
