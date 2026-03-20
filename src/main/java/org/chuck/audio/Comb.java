package org.chuck.audio;

/**
 * A comb filter UGen.
 * Adapted from STK.
 */
public class Comb extends ChuckUGen {
    private final Delay delayLine;
    private float coefficient = 0.7f;

    public Comb(int delaySamples) {
        this.delayLine = new Delay(delaySamples);
    }

    public void setCoefficient(float c) {
        this.coefficient = c;
    }

    @Override
    protected float compute(float input, long systemTime) {
        float temp = delayLine.getLastOut();
        float out = input + coefficient * temp;
        delayLine.tick(out, systemTime);
        return temp;
    }
}
