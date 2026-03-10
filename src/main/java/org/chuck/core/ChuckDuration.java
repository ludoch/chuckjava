package org.chuck.core;

import java.util.concurrent.TimeUnit;

/**
 * Represents a duration in ChucK (e.g., 10::ms).
 */
public record ChuckDuration(long samples) implements Comparable<ChuckDuration> {
    public static ChuckDuration of(long samples) {
        return new ChuckDuration(samples);
    }

    public static ChuckDuration fromMs(double ms, int sampleRate) {
        return new ChuckDuration((long) (ms * sampleRate / 1000.0));
    }

    public static ChuckDuration fromSeconds(double seconds, int sampleRate) {
        return new ChuckDuration((long) (seconds * sampleRate));
    }

    public ChuckDuration plus(ChuckDuration other) {
        return new ChuckDuration(this.samples + other.samples);
    }

    @Override
    public int compareTo(ChuckDuration o) {
        return Long.compare(this.samples, o.samples);
    }
}
