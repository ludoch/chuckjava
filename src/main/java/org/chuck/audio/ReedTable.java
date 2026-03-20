package org.chuck.audio;

/**
 * A reed table lookup.
 */
public class ReedTable extends ChuckUGen {
    private float offset = 0.6f;
    private float slope = -0.8f;

    public void setOffset(float offset) {
        this.offset = offset;
    }

    public void setSlope(float slope) {
        this.slope = slope;
    }

    @Override
    protected float compute(float input, long systemTime) {
        // Differential pressure across the reed
        float out = offset + (slope * input);
        // Symmetrical saturation
        if (out > 1.0f) out = 1.0f;
        if (out < -1.0f) out = -1.0f;
        return out;
    }
}
