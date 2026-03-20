package org.chuck.audio;

/**
 * An all-pass filter UGen.
 * Adapted from STK.
 */
public class AllPass extends ChuckUGen {
    private final Delay delayLine;
    private float coefficient = 0.7f;

    public AllPass(int delaySamples) {
        this.delayLine = new Delay(delaySamples);
    }

    public void setCoefficient(float c) {
        this.coefficient = c;
    }

    @Override
    protected float compute(float input, long systemTime) {
        float temp = delayLine.getLastOut();
        float inner = input + coefficient * temp;
        delayLine.tick(inner, systemTime);
        return -coefficient * inner + temp;
    }
}
