package org.chuck.audio.util;

import org.chuck.audio.osc.GenX;
import org.chuck.core.ChuckArray;

/**
 * CurveTable: Piecewise curve lookup table. ChucK: [ [0.0, 0.0, 0.0], [0.5, 1.0, 0.0], [1.0, 0.0,
 * 0.0] ] => curve.segments; Format: [time, value, curve] where time is 0.0 to 1.0.
 */
public class CurveTable extends GenX {
  public CurveTable(float sampleRate) {
    super(sampleRate);
  }

  public void segments(ChuckArray arr) {
    if (arr.size() < 3) return;

    // Fill table by interpolating between segments
    for (int i = 0; i < table.length; i++) {
      double x = (double) i / (table.length - 1);

      // Find bounding segments
      int s0 = -1, s1 = -1;
      for (int j = 0; j < arr.size(); j += 3) {
        double t = arr.getFloat(j);
        if (t <= x) s0 = j;
        if (t >= x && s1 == -1) s1 = j;
      }

      if (s0 == -1 && s1 != -1) table[i] = (float) arr.getFloat(s1 + 1);
      else if (s1 == -1 && s0 != -1) table[i] = (float) arr.getFloat(s0 + 1);
      else if (s0 != -1 && s1 != -1 && s0 != s1) {
        double t0 = arr.getFloat(s0), v0 = arr.getFloat(s0 + 1);
        double t1 = arr.getFloat(s1), v1 = arr.getFloat(s1 + 1);
        double alpha = (x - t0) / (t1 - t0);
        table[i] = (float) (v0 + alpha * (v1 - v0));
      } else if (s0 != -1) {
        table[i] = (float) arr.getFloat(s0 + 1);
      }
    }
  }

  @Override
  public void coeffs(float[] c) {
    // Not used directly, but required by abstract GenX
  }
}
