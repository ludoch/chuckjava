package org.chuck.audio;

/**
 * A simple Low Pass Filter.
 */
public class Lpf extends ChuckUGen {
    private float cutoff = 1000.0f;
    @SuppressWarnings("unused")
    private float resonance = 1.0f;
    private float sampleRate;
    
    // Filter state
    private float v0 = 0.0f;
    @SuppressWarnings("unused")
    private float v1 = 0.0f;

    public Lpf(float sampleRate) {
        this.sampleRate = sampleRate;
    }

    public void setCutoff(float cutoff) {
        this.cutoff = cutoff;
    }

    @Override
    public void tick(float[] buffer, int offset, int length, long systemTime) {
        float alpha = (float) (2.0 * Math.PI * cutoff / sampleRate);
        alpha = Math.min(Math.max(alpha, 0.0f), 1.0f);
        
        float localV0 = v0;
        for (int i = 0; i < length; i++) {
            float in = buffer[offset + i];
            localV0 = localV0 + alpha * (in - localV0);
            buffer[offset + i] = localV0;
        }
        v0 = localV0;
        
        lastOut = v0;
        lastTickTime = (systemTime == -1) ? -1 : systemTime + length - 1;
    }

    @Override
    protected float compute(float input, long systemTime) {
        // Simple 1-pole low pass for demonstration
        float alpha = (float) (2.0 * Math.PI * cutoff / sampleRate);
        alpha = Math.min(Math.max(alpha, 0.0f), 1.0f);
        
        v0 = v0 + alpha * (input - v0);
        return v0;
    }
}
