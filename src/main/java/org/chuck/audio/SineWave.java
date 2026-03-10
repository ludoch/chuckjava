package org.chuck.audio;

/**
 * A simple oscillator UGen.
 */
public class SineWave extends ChuckUGen {
    private float frequency = 440.0f;
    private float phase = 0.0f;
    private final float sampleRate;

    public SineWave(float sampleRate) {
        this.sampleRate = sampleRate;
    }

    public void setFrequency(float freq) {
        this.frequency = freq;
    }

    @Override
    protected float compute(float input) {
        // SineWave is a source, it ignores input unless used for FM/etc.
        float out = (float) Math.sin(phase);
        phase += 2.0 * Math.PI * frequency / sampleRate;
        if (phase >= 2.0 * Math.PI) {
            phase -= 2.0 * Math.PI;
        }
        return out;
    }
}
