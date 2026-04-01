package org.chuck.core;

/**
 * Standard library functions for ChucK.
 */
public class Std {
    /** Shared seeded Random instance for srandom/shuffle. */
    public static java.util.Random rng = new java.util.Random();

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

    /** Linear power to decibel. */
    public static double powtodb(double v) {
        if (v <= 0) return -100.0; // Approximation of -infinity
        return 10.0 * Math.log10(v);
    }

    /** RMS to decibel. */
    public static double rmstodb(double v) {
        if (v <= 0) return -100.0;
        return 20.0 * Math.log10(v);
    }

    /** Decibel to linear power. */
    public static double dbtopow(double v) {
        return Math.pow(10.0, v / 10.0);
    }

    /** Decibel to RMS. */
    public static double dbtorms(double v) {
        return Math.pow(10.0, v / 20.0);
    }

    /** Decibel to linear. */
    public static double dbtolin(double db) {
        return Math.pow(10.0, db / 20.0);
    }

    /** Linear to decibel. */
    public static double lintodb(double lin) {
        if (lin <= 0) return -100.0;
        return 20.0 * Math.log10(lin);
    }

    public static long atoi(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }

    public static double atof(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0.0; }
    }

    public static long rand2(long min, long max) {
        if (min > max) { long t = min; min = max; max = t; }
        return min + (long) (Math.random() * (max - min + 1));
    }

    public static double rand2f(double min, double max) {
        if (min > max) { double t = min; min = max; max = t; }
        return min + Math.random() * (max - min);
    }

    public static double fabs(double v) {
        return Math.abs(v);
    }

    public static void srand(long seed) {
        // Java's Math.random() doesn't expose seed easily, 
        // but for now we'll just ignore or use a shared Random instance.
    }

    public static long systemTime() {
        return System.nanoTime();
    }

    public static String getenv(String key) {
        if (key == null) return null;
        String v = System.getenv(key);
        if (v == null) v = System.getProperty(key);
        return v;
    }

    public static String getenv(String key, String defaultValue) {
        String v = getenv(key);
        return v != null ? v : defaultValue;
    }

    public static void setenv(String key, String value) {
        // System.setProperty is the Java equivalent for most cases
        System.setProperty(key, value);
    }

    /** ChucK 1.5.1.1+: Std.range(n) -> [0, 1, ..., n-1] */
    public static ChuckArray range(long n) {
        int sz = (int) Math.max(0, n);
        ChuckArray arr = new ChuckArray(ChuckType.ARRAY, sz);
        for (int i = 0; i < sz; i++) arr.setInt(i, i);
        return arr;
    }

    public static ChuckArray range(long start, long end) {
        int sz = (int) Math.max(0, end - start);
        ChuckArray arr = new ChuckArray(ChuckType.ARRAY, sz);
        for (int i = 0; i < sz; i++) arr.setInt(i, start + i);
        return arr;
    }

    public static ChuckArray range(long start, long end, long step) {
        if (step == 0) return new ChuckArray(ChuckType.ARRAY, 0);
        int sz = (int) Math.max(0, (end - start) / step);
        ChuckArray arr = new ChuckArray(ChuckType.ARRAY, sz);
        for (int i = 0; i < sz; i++) arr.setInt(i, start + i * step);
        return arr;
    }

    /** Convert int to string. */
    public static String itoa(long i) {
        return Long.toString(i);
    }

    /** Convert float to string with the given number of decimal places. */
    public static String ftoa(double f, long decimals) {
        return String.format("%." + Math.max(0, decimals) + "f", f);
    }

    /** Convert float to int (truncate). */
    public static long ftoi(double f) {
        return (long) f;
    }

    /** Sign of a value: -1, 0, or 1. */
    public static double sgn(double v) {
        if (v > 0.0) return 1.0;
        if (v < 0.0) return -1.0;
        return 0.0;
    }

    /** Scale a value from [srcMin, srcMax] to [dstMin, dstMax]. */
    public static double scalef(double val, double srcMin, double srcMax, double dstMin, double dstMax) {
        if (srcMax == srcMin) return dstMin;
        return dstMin + (val - srcMin) / (srcMax - srcMin) * (dstMax - dstMin);
    }

    /** Absolute value of an integer. */
    public static long abs(long v) {
        return Math.abs(v);
    }
}
