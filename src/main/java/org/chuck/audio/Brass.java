package org.chuck.audio;

/**
 * Brass: STK lip-reed physical model.
 */
public class Brass extends ChuckUGen {
    private final DelayL delayLine;
    private final OnePole filter;
    private final Adsr adsr;
    
    private float lipFilter = 0.0f;
    private float pressure = 0.0f;
    private final float sampleRate;

    public Brass(float sampleRate) {
        this.sampleRate = sampleRate;
        int maxDelay = (int) (sampleRate / 20.0);
        delayLine = new DelayL(maxDelay);
        filter = new OnePole();
        filter.setPole(0.7f);
        adsr = new Adsr(sampleRate);
        adsr.set(0.05f, 0.05f, 0.9f, 0.1f);
        
        setFreq(440.0);
    }

    public void setFreq(double f) {
        double delay = sampleRate / f - 1.0;
        delayLine.setDelay(delay);
    }

    public void noteOn(float velocity) {
        pressure = 0.1f + velocity * 0.9f;
        adsr.keyOn();
    }

    public void noteOff(float velocity) {
        adsr.keyOff();
    }

    public void lip(float val) { this.lipFilter = val; }

    @Override
    protected float compute(float input) {
        float env = adsr.tick();
        float breath = pressure * env;
        
        float boreRes = delayLine.getLastOut();
        float jawRes = breath - boreRes;
        
        // Non-linear lip function (simplified)
        float lipOutput = jawRes * (1.0f - (jawRes * jawRes));
        
        float out = delayLine.tick(breath + filter.tick(lipOutput));
        
        lastOut = out;
        return out;
    }
}
