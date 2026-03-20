package org.chuck.audio;

/**
 * Flute: STK physical model of a flute.
 * Uses a jet-reflection waveguide with non-linear feedback.
 */
public class Flute extends ChuckUGen {
    private final DelayL delayLine;
    private final OnePole filter;
    private final Adsr adsr;
    private final Noise noise;
    
    private float jetDelay;
    private float noiseGain = 0.05f;
    private float endReflection = 0.5f;
    private float jetReflection = 0.5f;
    private float pressure = 0.0f;
    private final float sampleRate;

    public Flute(float sampleRate) {
        this.sampleRate = sampleRate;
        int maxDelay = (int) (sampleRate / 20.0);
        delayLine = new DelayL(maxDelay);
        filter = new OnePole();
        filter.setPole(0.8f);
        adsr = new Adsr(sampleRate);
        adsr.set(0.1f, 0.1f, 0.8f, 0.1f);
        noise = new Noise();
        
        setFreq(440.0);
    }

    public void setFreq(double f) {
        double delay = sampleRate / f - 2.0;
        delayLine.setDelay(delay);
        jetDelay = (float) (delay * 0.5);
    }

    public void noteOn(float velocity) {
        pressure = 0.1f + velocity * 0.9f;
        adsr.keyOn();
    }

    public void noteOff(float velocity) {
        adsr.keyOff();
    }

    @Override
    protected float compute(float input, long systemTime) {
        float env = adsr.tick(systemTime, systemTime);
        float breath = pressure * env + noise.tick(systemTime, systemTime) * noiseGain;
        
        float boreRes = delayLine.getLastOut();
        float jetRes = breath + boreRes * jetReflection;
        
        // Non-linear jet function (simplified)
        float jetOutput = jetRes - (jetRes * jetRes * jetRes);
        
        float out = delayLine.tick(breath + filter.tick(jetOutput * endReflection, systemTime), systemTime);
        
        lastOut = out;
        return out;
    }
}
