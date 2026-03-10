package org.chuck.audio;

/**
 * Triangle wave oscillator.
 *
 * Triangle is C0-continuous (no amplitude discontinuities) and its harmonics
 * roll off as 1/n², so aliasing is inherently low. Unlike SawOsc and PulseOsc,
 * no PolyBLEP correction is needed or applied here.
 */
public class TriOsc extends Osc {
    public TriOsc(float sampleRate) {
        super(sampleRate);
    }

    @Override
    protected double computeOsc(double phase) {
        // Shift phase by 0.25 to match ChucK's TriOsc definition
        double p = phase + 0.25;
        if (p > 1.0) p -= 1.0;

        if (p < width) {
            return (width == 0.0) ? 1.0 : -1.0 + 2.0 * p / width;
        } else {
            return (width == 1.0) ? 0.0 : 1.0 - 2.0 * (p - width) / (1.0 - width);
        }
    }
}
