package org.chuck.audio;

/**
 * FilterStk — STK-compatible filter base (passthrough; concrete filters subclass this).
 * Wraps FilterBasic with STK-style gain and clear() method.
 */
public class FilterStk extends FilterBasic {
    protected double gain = 1.0;

    public FilterStk(float sampleRate) { super(sampleRate); }

    public void gain(double g) { this.gain = g; }
    public double gain()       { return gain; }
    public void clear()        { /* reset internal state — subclasses override */ }

    @Override
    protected float compute(float input, long systemTime) { return (float)(input * gain); }
}
