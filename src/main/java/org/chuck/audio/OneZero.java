package org.chuck.audio;

/**
 * A one-zero digital filter.
 * y[n] = b0 * x[n] + b1 * x[n-1]
 */
public class OneZero extends ChuckUGen {
    private float b0 = 1.0f;
    private float b1 = 0.0f;
    private float lastInput = 0.0f;

    public void setB0(float b0) {
        this.b0 = b0;
    }

    public void setB1(float b1) {
        this.b1 = b1;
    }

    public void setZero(float zero) {
        if (zero > 0.0f) b0 = 1.0f / (1.0f + zero);
        else b0 = 1.0f / (1.0f - zero);
        b1 = -zero * b0;
    }

    @Override
    protected float compute(float input) {
        float out = b0 * input + b1 * lastInput;
        lastInput = input;
        return out;
    }
}
