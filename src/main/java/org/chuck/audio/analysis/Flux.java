package org.chuck.audio.analysis;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.UAna;
import org.chuck.audio.UAnaBlob;

/**
 * Flux: Spectral Flux unit analyzer. Measures the difference between consecutive spectral frames.
 */
public class Flux extends UAna {
  private float[] prevMagnitudes;

  @Override
  protected void computeUAna() {
    UAnaBlob input = null;
    for (ChuckUGen src : getSources()) {
      if (src instanceof UAna u) {
        input = u.getLastBlob();
        break;
      }
    }

    if (input == null) return;
    float[] current = input.getFvals();
    if (current.length == 0) return;

    if (prevMagnitudes == null || prevMagnitudes.length != current.length) {
      prevMagnitudes = new float[current.length];
    }

    float flux = 0;
    for (int i = 0; i < current.length; i++) {
      float diff = current[i] - prevMagnitudes[i];
      if (diff > 0) flux += diff; // positive flux only
      prevMagnitudes[i] = current[i];
    }

    lastBlob.setFvals(new float[] {flux});
  }
}
