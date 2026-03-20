package org.chuck.audio;

/**
 * SubNoise: Sub-sampled noise generator.
 */
public class SubNoise extends ChuckUGen {
    private float lastValue = 0.0f;
    private int rate = 1;
    private int count = 0;

    @Override
    protected float compute(float input, long systemTime) {
        if (count == 0) {
            lastValue = (float)(Math.random() * 2.0 - 1.0);
        }
        count = (count + 1) % Math.max(1, rate);
        return lastValue;
    }

    public void rate(int r) { this.rate = r; }
    public int rate() { return rate; }
}
