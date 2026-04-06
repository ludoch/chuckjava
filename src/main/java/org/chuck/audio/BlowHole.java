package org.chuck.audio;

/**
 * BlowHole — Clarinet model with tonehole and register vent.
 * Extends the basic Clarinet bore model with a side-hole that creates
 * register transitions and overblown harmonics.
 */
public class BlowHole extends ChuckUGen {
    private final DelayL bore;
    private final DelayL tonehole;
    private final OnePole filter;
    private final Noise   noise;
    private final Adsr    env;
    private float pressure  = 0.5f;
    private float noiseGain = 0.02f;
    private float toneCoeff = 0.9f; // tonehole reflection coefficient (0=open, 1=closed)
    private float ventCoeff = 0.0f; // register vent (0=closed, >0 opens higher register)
    private double freq = 220.0;
    private final float sampleRate;

    public BlowHole(float sr) {
        this.sampleRate = sr;
        bore     = new DelayL((int)(sr * 0.05));
        tonehole = new DelayL(10);
        filter   = new OnePole();
        filter.setPole(0.9f);
        noise    = new Noise();
        env      = new Adsr(sr);
        env.set(0.005f, 0.01f, 0.9f, 0.05f);
        setFreq(220.0);
    }

    public void setFreq(double f) {
        freq = f;
        double delay = sampleRate / f - 2.0;
        bore.setDelay(Math.max(1.0, delay));
    }

    /** Tonehole openness 0.0 (closed) to 1.0 (fully open). */
    public void tonehole(double v)   { toneCoeff = (float)(1.0 - v); }
    /** Register vent openness 0.0–1.0; opening shifts to upper register. */
    public void vent(double v)       { ventCoeff = (float) v; }
    public void noteOn(float vel)    { pressure = 0.4f + vel * 0.6f; env.keyOn(); }
    public void noteOff(float vel)   { env.keyOff(); }

    @Override
    protected float compute(float input, long t) {
        float e     = env.tick(t);
        float breath = pressure * e + noise.tick(t) * noiseGain;
        float boreOut = bore.getLastOut();

        // Tonehole junction: attenuates reflection based on openness
        float reflected = boreOut * toneCoeff;

        // Reed non-linearity (simplified)
        float reedInput = breath - reflected;
        float reed = reedInput - reedInput * reedInput * reedInput * 0.33f;

        // Register vent: partial reflection from the vent position
        float out = filter.tick(reed - ventCoeff * boreOut, t);
        bore.tick(out, t);

        lastOut = boreOut * gain;
        return lastOut;
    }
}
