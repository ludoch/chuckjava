package org.chuck.audio.analysis;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.UAna;
import org.chuck.audio.UAnaBlob;

/**
 * Rolloff: Spectral Rolloff unit analyzer. Measures the frequency below which a certain percentage
 * of energy resides.
 */
public class Rolloff extends UAna {
  private float percent = 0.85f;

  public void percent(float p) {
    this.percent = p;
  }

  public float percent() {
    return percent;
  }

  @Override
  protected void computeUAna() {
    UAnaBlob input = null;
    for (ChuckUGen src : sources) {
      if (src instanceof UAna u) {
        input = u.getLastBlob();
        break;
      }
    }

    if (input == null) return;
    float[] magnitudes = input.getFvals();
    if (magnitudes.length == 0) return;

    float totalEnergy = 0;
    for (float m : magnitudes) totalEnergy += m;

    float targetEnergy = totalEnergy * percent;
    float currentEnergy = 0;
    int bin = 0;
    for (int i = 0; i < magnitudes.length; i++) {
      currentEnergy += magnitudes[i];
      if (currentEnergy >= targetEnergy) {
        bin = i;
        break;
      }
    }

    // Return normalized frequency (0.0 to 1.0)
    lastBlob.setFvals(new float[] {(float) bin / magnitudes.length});
  }
}
