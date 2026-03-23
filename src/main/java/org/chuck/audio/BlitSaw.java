package org.chuck.audio;

/**
 * Band-limited sawtooth oscillator using PolyBLEP anti-aliasing.
 * Controls: freq (Hz), gain, phase.
 */
public class BlitSaw extends Osc {

    public BlitSaw(float sampleRate) {
        super(sampleRate);
    }

    @Override
    protected double computeOsc(double phase) {
        double dt = freq / sampleRate;
        // Naive sawtooth: 2*phase - 1 (range [-1, 1])
        double saw = 2.0 * phase - 1.0;
        // Apply PolyBLEP correction at the discontinuity (phase = 0)
        saw -= polyBlep(phase, dt);
        return saw;
    }
}
