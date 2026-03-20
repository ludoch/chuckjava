package org.chuck.audio;

/**
 * PitShift UGen — pitch shifter stub.
 * Minimal implementation for compatibility; passes audio through unchanged.
 */
public class PitShift extends ChuckUGen {
    private float shift = 1.0f;

    @Override
    protected float compute(float input, long systemTime) {
        return input;
    }

    public float shift() { return shift; }
    public void setShift(float s) { this.shift = s; }
    public float getShift() { return shift; }

    public int mix() { return 0; }
    public void setMix(int m) {}
}
