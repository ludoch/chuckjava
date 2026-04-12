package org.chuck.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Lightweight object pooling for frequently allocated ChucK types. Helps reduce GC pressure in
 * high-frequency audio threads.
 */
public class ChuckObjectPool {

  private static final int MAX_POOL_SIZE = 1024;

  private static final Deque<ChuckDuration> durationPool = new ArrayDeque<>(MAX_POOL_SIZE);
  private static final Deque<ChuckString> stringPool = new ArrayDeque<>(MAX_POOL_SIZE);

  public static ChuckDuration getDuration(double samples) {
    synchronized (durationPool) {
      if (!durationPool.isEmpty()) {
        ChuckDuration d = durationPool.pop();
        d.setSamples(samples);
        return d;
      }
    }
    return new ChuckDuration(samples);
  }

  public static void releaseDuration(ChuckDuration d) {
    if (d == null) return;
    synchronized (durationPool) {
      if (durationPool.size() < MAX_POOL_SIZE) {
        durationPool.push(d);
      }
    }
  }

  public static ChuckString getString(String value) {
    synchronized (stringPool) {
      if (!stringPool.isEmpty()) {
        ChuckString s = stringPool.pop();
        s.setValue(value);
        return s;
      }
    }
    return new ChuckString(value);
  }

  public static void releaseString(ChuckString s) {
    if (s == null) return;
    synchronized (stringPool) {
      if (stringPool.size() < MAX_POOL_SIZE) {
        stringPool.push(s);
      }
    }
  }
}
