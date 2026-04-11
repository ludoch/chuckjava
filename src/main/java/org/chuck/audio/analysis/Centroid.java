package org.chuck.audio.analysis;

import java.util.Collections;
import org.chuck.audio.ChuckUGen;
import org.chuck.audio.UAna;
import org.chuck.audio.util.Complex;

/**
 * Spectral Centroid Unit Analyzer. Measures the "brightness" of a spectrum (weighted average of
 * frequencies).
 *
 * <p>Usually connected after an FFT: SinOsc s => FFT f => Centroid c => blackhole;
 */
public class Centroid extends UAna {

  @Override
  protected void computeUAna() {
    float sumMag = 0;
    float weightedSum = 0;

    for (ChuckUGen src : sources) {
      if (src instanceof UAna u) {
        float[] mags = u.getLastBlob().getFvals();
        if (mags.length > 0) {
          for (int i = 0; i < mags.length; i++) {
            float mag = mags[i];
            sumMag += mag;
            weightedSum += i * mag;
          }
        }
      }
    }

    float centroid = (sumMag > 0) ? (weightedSum / sumMag) : 0;
    // Normalize centroid to 0.0 - 1.0 range (relative to half the FFT size)
    // If we want raw bin index, don't normalize. ChucK returns bin index.

    lastBlob.setCvals(Collections.singletonList(new Complex(centroid, 0)));
  }
}
