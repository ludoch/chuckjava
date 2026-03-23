package org.chuck.audio;

/**
 * Band-limited square oscillator using PolyBLEP anti-aliasing.
 * Controls: freq (Hz), width (pulse width, default 0.5), gain, phase.
 */
public class BlitSquare extends Osc {

    public BlitSquare(float sampleRate) {
        super(sampleRate);
    }

    @Override
    protected double computeOsc(double phase) {
        double dt = freq / sampleRate;
        double w = width; // pulse width in [0, 1]

        // Naive square: +1 for phase < width, -1 otherwise
        double sq = (phase < w) ? 1.0 : -1.0;

        // PolyBLEP correction at rising edge (phase = 0) — add +2 discontinuity
        sq += polyBlep(phase, dt);
        // PolyBLEP correction at falling edge (phase = width) — subtract 2 discontinuity
        sq -= polyBlep((phase - w + 1.0) % 1.0, dt);

        return sq;
    }
}
