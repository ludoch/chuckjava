package org.chuck.core;

/** Represents a duration in ChucK (e.g., 10::ms). Immutable. */
public final class ChuckDuration extends ChuckObject implements Comparable<ChuckDuration> {
  private final double samples;

  public ChuckDuration(double samples) {
    super(ChuckType.DUR);
    this.samples = samples;
  }

  public double samples() {
    return samples;
  }

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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ChuckDuration that = (ChuckDuration) o;
    return Double.compare(that.samples, samples) == 0;
  }

  @Override
  public int hashCode() {
    return Double.hashCode(samples);
  }

  @Override
  public String toString() {
    return "ChuckDuration(" + samples + ")";
  }
}
