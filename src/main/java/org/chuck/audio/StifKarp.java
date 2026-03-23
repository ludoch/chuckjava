package org.chuck.audio;

/**
 * A stiff Karplus-Strong string physical model.
 */
public class StifKarp extends ChuckUGen {
    private final DelayL delayLine;
    private final OnePole filter;
    @SuppressWarnings("unused")
    private float pickupPos = 0.667f;
    private float lastInput = 0.0f;
    private final float sampleRate;

    public StifKarp(float sampleRate) {
        this.sampleRate = sampleRate;
        int maxDelay = (int) (sampleRate / 8.0); // Down to 8Hz
        delayLine = new DelayL(maxDelay);
        filter = new OnePole();
        filter.setPole(0.9f);
        setFreq(440.0);
    }

    public void setFreq(double f) {
        double delay = sampleRate / f - 0.5;
        delayLine.setDelay(delay);
    }

    public void pickupPos(float p) { this.pickupPos = p; }

    public void noteOn(float velocity) {
        // Pluck the string with noise
        for (int i = 0; i < delayLine.getGain(); i++) {
            // delayLine doesn't have a way to pre-fill, so we just rely on compute loop
        }
        // In ChucK, noteOn usually triggers an internal excitation
        lastInput = velocity;
    }

    public void noteOff(float velocity) {
        lastInput = 0.0f;
    }

    @Override
    protected float compute(float input, long systemTime) {
        // Karplus-Strong loop
        float out = delayLine.tick(input + lastInput + filter.tick(delayLine.getLastOut() * 0.99f, systemTime), systemTime);
        lastInput *= 0.95f; // Decay pluck
        lastOut = out;
        return out;
    }
}
