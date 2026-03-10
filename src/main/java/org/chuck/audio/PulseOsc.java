package org.chuck.audio;

/**
 * Band-limited pulse wave oscillator using PolyBLEP correction.
 *
 * A pulse wave has two discontinuities per cycle: a rising edge at phase=0 and
 * a falling edge at phase=width. Both are corrected independently with PolyBLEP,
 * giving clean output even at high frequencies.
 */
public class PulseOsc extends Osc {
    public PulseOsc(float sampleRate) {
        super(sampleRate);
    }

    @Override
    protected double computeOsc(double phase) {
        double dt = freq / sampleRate;

        // Naive pulse
        double out = (phase < width) ? 1.0 : -1.0;

        // Rising edge at phase = 0
        out += polyBlep(phase, dt);

        // Falling edge at phase = width
        double t2 = phase - width;
        if (t2 < 0.0) t2 += 1.0;
        out -= polyBlep(t2, dt);

        return out;
    }
}
