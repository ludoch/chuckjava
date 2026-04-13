package org.chuck.audio.util;

/** Registry of standard wavetables (mirrors STK rawwaves). */
public class WavetableRegistry {

  /** Returns a 256-sample glottal pulse (impuls10 equivalent). */
  public static float[] getGlottalPulse() {
    float[] table = new float[256];
    for (int i = 0; i < 256; i++) {
      double t = (double) i / 256.0;
      // Rosenberg glottal pulse formula
      if (t < 0.4) {
        table[i] = (float) (0.5 * (1.0 - Math.cos(Math.PI * t / 0.4)));
      } else if (t < 0.6) {
        table[i] = (float) (Math.cos(Math.PI * (t - 0.4) / 0.4));
      } else {
        table[i] = 0.0f;
      }
    }
    return table;
  }

  /** Returns a noise-based pluck excitation. */
  public static float[] getPluckExcitation(int size) {
    float[] table = new float[size];
    for (int i = 0; i < size; i++) {
      table[i] = (float) (Math.random() * 2.0 - 1.0);
      // Linear decay
      table[i] *= (1.0f - (float) i / size);
    }
    return table;
  }
}
