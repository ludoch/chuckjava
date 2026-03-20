package org.chuck.audio;

/**
 * Sitar: STK physical model of a sitar.
 * Uses a plucked string with a non-linear bridge reflection.
 */
public class Sitar extends ChuckUGen {
    private final DelayL delayLine;
    private final OnePole filter;
    private final Noise noise;
    private final float sampleRate;
    private float loopGain = 0.99f;
    private float ampmult = 0.0f;

    public Sitar(float sampleRate) {
        this.sampleRate = sampleRate;
        int maxDelay = (int) (sampleRate / 20.0);
        delayLine = new DelayL(maxDelay);
        filter = new OnePole();
        filter.setPole(0.9f);
        noise = new Noise();
        setFreq(440.0);
    }

    public void setFreq(double f) {
        double delay = sampleRate / f - 0.5;
        delayLine.setDelay(delay);
    }

    public void noteOn(float velocity) {
        ampmult = velocity;
    }

    @Override
    protected float compute(float input, long systemTime) {
        float excitation = noise.tick(systemTime) * ampmult;
        ampmult *= 0.95f; // Decay the pluck excitation

        float out = delayLine.tick(excitation + filter.tick(delayLine.getLastOut() * loopGain, systemTime), systemTime);

        // Sitar bridge non-linearity (simplified)
        if (out > 0.1f) out *= 0.9f; 

        lastOut = out;
        return out;
    }
}
