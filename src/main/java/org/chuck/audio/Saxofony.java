package org.chuck.audio;

/**
 * Saxofony: STK woodwind physical model.
 * Uses a reed-table waveguide implementation.
 */
public class Saxofony extends ChuckUGen {
    private final DelayL delayLine;
    private final ReedTable reedTable;
    private final OneZero filter;
    private final Adsr adsr;
    
    private float pressure = 0.0f;
    private final float sampleRate;

    public Saxofony(float sampleRate) {
        this.sampleRate = sampleRate;
        int maxDelay = (int) (sampleRate / 20.0);
        delayLine = new DelayL(maxDelay);
        reedTable = new ReedTable();
        filter = new OneZero();
        adsr = new Adsr(sampleRate);
        adsr.set(0.1f, 0.1f, 0.7f, 0.1f);
        
        setFreq(440.0);
    }

    public void setFreq(double f) {
        double delay = sampleRate / f - 1.5;
        delayLine.setDelay(delay);
    }

    public void noteOn(float velocity) {
        pressure = 0.2f + velocity * 0.8f;
        adsr.keyOn();
    }

    public void noteOff(float velocity) {
        adsr.keyOff();
    }

    @Override
    protected float compute(float input, long systemTime) {
        float env = adsr.tick(systemTime);
        float breath = pressure * env;

        float boreRes = -0.9f * filter.tick(delayLine.getLastOut(), systemTime);
        float pressureDiff = breath - boreRes;

        float out = delayLine.tick(breath + pressureDiff * reedTable.tick(pressureDiff), systemTime);

        lastOut = out;
        return out;
    }

}
