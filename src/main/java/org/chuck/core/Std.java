package org.chuck.core;

/**
 * Standard library functions for ChucK.
 */
public class Std {
    /**
     * Converts a MIDI note number to a frequency in Hertz.
     */
    public static double mtof(double midiNote) {
        return 440.0 * Math.pow(2.0, (midiNote - 69.0) / 12.0);
    }

    /**
     * Converts a frequency in Hertz to a MIDI note number.
     */
    public static double ftom(double freq) {
        return 69.0 + 12.0 * (Math.log(freq / 440.0) / Math.log(2.0));
    }

    public static long clamp(long val, long lo, long hi) {
        return Math.max(lo, Math.min(hi, val));
    }

    public static double clampf(double val, double lo, double hi) {
        return Math.max(lo, Math.min(hi, val));
    }

    public static double dbtolin(double db) {
        return Math.pow(10.0, db / 20.0);
    }

    public static double lintodb(double lin) {
        if (lin <= 0) return Double.NEGATIVE_INFINITY;
        return 20.0 * Math.log10(lin);
    }
}
