package org.chuck.audio;

/**
 * A non-linear friction function for bowed string modeling.
 */
public class BowTable {
    private float offset = 0.0f;
    private float slope = 0.1f;

    public void setOffset(float offset) { this.offset = offset; }
    public void setSlope(float slope) { this.slope = slope; }

    public float lookup(float input) {
        float sample = input + offset;
        sample *= slope;
        float absSample = Math.abs(sample);
        float output = (float) Math.pow(absSample + 0.75f, -4.0);
        return Math.min(1.0f, output);
    }
}
