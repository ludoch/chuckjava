package org.chuck.audio;

/**
 * Second-order band-reject (notch) filter.
 * Controls: freq (notch center Hz), Q (resonance, default 1.0).
 */
public class BRF extends ChuckUGen {
    private double cutoff;
    private double q;
    private final float sampleRate;

    private double b0, b1, b2;
    private double a1, a2;
    private double x1 = 0, x2 = 0, y1 = 0, y2 = 0;

    public BRF(float sampleRate) {
        this.sampleRate = sampleRate;
        this.cutoff = 1000.0;
        this.q = 1.0;
        updateCoeffs();
    }

    public double freq(double f) { cutoff = f; updateCoeffs(); return f; }
    public double freq() { return cutoff; }
    public double Q(double qv) { q = qv; updateCoeffs(); return qv; }
    public double Q() { return q; }

    private void updateCoeffs() {
        double w0 = 2.0 * Math.PI * cutoff / sampleRate;
        double cosW0 = Math.cos(w0);
        double alpha = Math.sin(w0) / (2.0 * q);
        double norm = 1.0 / (1.0 + alpha);
        b0 =  norm;
        b1 = -2.0 * cosW0 * norm;
        b2 =  norm;
        a1 = -2.0 * cosW0 * norm;
        a2 =  (1.0 - alpha) * norm;
    }

    @Override
    protected float compute(float input, long systemTime) {
        double x0 = input;
        double y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
        x2 = x1; x1 = x0;
        y2 = y1; y1 = y0;
        return (float) y0;
    }
}
