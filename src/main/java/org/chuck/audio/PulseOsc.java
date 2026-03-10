package org.chuck.audio;

/**
 * A pulse wave oscillator.
 */
public class PulseOsc extends Osc {
    public PulseOsc(float sampleRate) {
        super(sampleRate);
    }

    @Override
    protected double computeOsc(double phase) {
        return (phase < width) ? 1.0 : -1.0;
    }
}
