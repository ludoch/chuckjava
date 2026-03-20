package org.chuck.audio;

/**
 * A Unit Generator that outputs a constant value.
 */
public class Step extends ChuckUGen {
    private float nextValue = 1.0f;

    public void setNext(float val) {
        this.nextValue = val;
    }

    @Override
    protected float compute(float input, long systemTime) {
        return nextValue;
    }
}
