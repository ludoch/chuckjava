package org.chuck.audio;

/**
 * Band-limited sawtooth wave oscillator using PolyBLEP correction.
 *
 * The naive sawtooth (2t − 1, reset at t=1→0) has a single discontinuity per
 * cycle. PolyBLEP smooths that jump over one sample period, eliminating the
 * worst aliasing harmonics with negligible CPU cost.
 */
public class SawOsc extends Osc {
    public SawOsc(float sampleRate) {
        super(sampleRate);
        this.freq = 440.0;
    }

    /** Backward-compat alias (ChucK scripts use setFrequency). */
    public void setFrequency(float f) { setFreq(f); }

    @Override
    protected double computeOsc(double phase) {
        double dt  = freq / sampleRate;
        double saw = 2.0 * phase - 1.0;  // naive sawtooth [-1, +1)
        saw -= polyBlep(phase, dt);       // correct the reset discontinuity at t=0
        return saw;
    }
}
