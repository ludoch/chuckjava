package org.chuck.audio;

/**
 * A phasor oscillator; linearly rises from 0 to 1.
 */
public class Phasor extends Osc {
    public Phasor(float sampleRate) {
        super(sampleRate);
    }

    @Override
    protected double computeOsc(double phase) {
        return phase;
    }
}
