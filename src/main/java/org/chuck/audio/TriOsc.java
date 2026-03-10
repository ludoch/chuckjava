package org.chuck.audio;

/**
 * A triangle wave oscillator.
 */
public class TriOsc extends Osc {
    public TriOsc(float sampleRate) {
        super(sampleRate);
    }

    @Override
    protected double computeOsc(double phase) {
        // Shift phase by 0.25 to match ChucK's TriOsc implementation
        double p = phase + 0.25;
        if (p > 1.0) p -= 1.0;

        if (p < width) {
            return (width == 0.0) ? 1.0 : -1.0 + 2.0 * p / width;
        } else {
            return (width == 1.0) ? 0 : 1.0 - 2.0 * (p - width) / (1.0 - width);
        }
    }
}
