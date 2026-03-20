package org.chuck.audio;

/**
 * A Unit Generator that outputs a single non-zero value (impulse), then 0.
 */
public class Impulse extends ChuckUGen {
    private float nextValue = 0.0f;

    public void setNext(float val) {
        this.nextValue = val;
    }

    @Override
    protected float compute(float input, long systemTime) {
        float out = nextValue;
        nextValue = 0.0f;
        return out;
    }
}
