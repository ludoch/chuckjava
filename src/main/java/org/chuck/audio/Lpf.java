package org.chuck.audio;

/**
 * A simple Low Pass Filter.
 */
public class Lpf extends ChuckUGen {
    private float cutoff = 1000.0f;
    private float resonance = 1.0f;
    private float sampleRate;
    
    // Filter state
    private float v0 = 0.0f;
    private float v1 = 0.0f;

    public Lpf(float sampleRate) {
        this.sampleRate = sampleRate;
    }

    public void setCutoff(float cutoff) {
        this.cutoff = cutoff;
    }

    @Override
    protected float compute(float input) {
        // Simple 1-pole low pass for demonstration
        float alpha = (float) (2.0 * Math.PI * cutoff / sampleRate);
        alpha = Math.min(Math.max(alpha, 0.0f), 1.0f);
        
        v0 = v0 + alpha * (input - v0);
        return v0;
    }
}
