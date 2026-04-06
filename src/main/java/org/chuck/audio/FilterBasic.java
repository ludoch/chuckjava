package org.chuck.audio;

/**
 * FilterBasic — abstract base class for simple one-pole/one-zero filters.
 * Exposes freq(double), Q(double), set(double, double) API used by LPF/HPF/BPF/BRF.
 * In Java this is a concrete passthrough; subclasses override compute().
 */
public class FilterBasic extends ChuckUGen {
    protected double freq = 1000.0;
    protected double Q    = 1.0;
    protected float  sampleRate;

    public FilterBasic(float sampleRate) {
        this.sampleRate = sampleRate;
    }

    public void freq(double f)       { this.freq = f; onParamChange(); }
    public double freq()             { return freq; }
    public void Q(double q)          { this.Q = q;    onParamChange(); }
    public double Q()                { return Q; }
    public void set(double f, double q) { this.freq = f; this.Q = q; onParamChange(); }

    protected void onParamChange() {}

    @Override
    protected float compute(float input, long systemTime) { return input; }
}
