package org.chuck.audio;

/**
 * A sine wave oscillator.
 */
public class SinOsc extends Osc {
    public SinOsc(float sampleRate) {
        super(sampleRate);
    }

    @Override
    protected double computeOsc(double phase) {
        return Math.sin(phase * 2.0 * Math.PI);
    }
}
