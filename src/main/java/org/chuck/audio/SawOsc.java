package org.chuck.audio;

/**
 * A sawtooth wave oscillator.
 */
public class SawOsc extends ChuckUGen {
    private float frequency = 440.0f;
    private float phase = 0.0f;
    private final float sampleRate;

    public SawOsc(float sampleRate) {
        this.sampleRate = sampleRate;
    }

    public void setFrequency(float freq) {
        this.frequency = freq;
    }

    @Override
    protected float compute(float input) {
        // Output ranges from -1.0 to 1.0
        float out = (float) (2.0 * (phase / (2.0 * Math.PI)) - 1.0);
        
        phase += 2.0 * Math.PI * frequency / sampleRate;
        if (phase >= 2.0 * Math.PI) {
            phase -= 2.0 * Math.PI;
        }
        return out;
    }
}
