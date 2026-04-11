package org.chuck.core;

/** Represents a duration in ChucK (e.g., 10::ms). */
public record ChuckDuration(double samples) implements Comparable<ChuckDuration> {
  public static ChuckDuration of(double samples) {
    return new ChuckDuration(samples);
  }

  public static ChuckDuration fromMs(double ms, int sampleRate) {
    return new ChuckDuration(ms * sampleRate / 1000.0);
  }

  public static ChuckDuration fromSeconds(double seconds, int sampleRate) {
    return new ChuckDuration(seconds * sampleRate);
  }

  public ChuckDuration plus(ChuckDuration other) {
    return new ChuckDuration(this.samples + other.samples);
  }

  @Override
  public int compareTo(ChuckDuration o) {
    return Double.compare(this.samples, o.samples);
  }
}
