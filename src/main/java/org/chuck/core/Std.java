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
}
