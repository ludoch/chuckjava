package org.chuck.core;

/** Global pool for frequently created ChucK objects to reduce GC pressure. */
public class ChuckObjectPool {
  // Disabled for now due to premature release issues with variables

  public static ChuckDuration getDuration(double samples) {
    return new ChuckDuration(samples);
  }

  public static void releaseDuration(ChuckDuration d) {
    // Do nothing
  }

  public static ChuckString getString(String val) {
    return new ChuckString(val);
  }

  public static void releaseString(ChuckString s) {
    // Do nothing
  }
}
