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
    protected float compute(float input) {
        float temp = delayLine.getLastOut();
        float out = -coefficient * input + temp + coefficient * (input + coefficient * temp);
        // Simplified STK implementation
        float inner = input + coefficient * temp;
        delayLine.tick(inner);
        return -coefficient * inner + temp;
    }
}
