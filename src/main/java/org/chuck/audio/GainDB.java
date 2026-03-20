package org.chuck.audio;

/**
 * Gain expressed in decibels. 0 dB = unity gain.
 */
public class GainDB extends ChuckUGen {
    private double db = 0.0;

    public GainDB() { setDb(0.0); }
    public GainDB(double db) { setDb(db); }

    public void setDb(double dBValue) {
        this.db = dBValue;
        setGain((float) Math.pow(10.0, dBValue / 20.0));
    }
    public float db(float db) { setDb(db); return db; }
    public float db()         { return (float) db; }
    public float gain(float g) { setGain(g); this.db = 20.0 * Math.log10(g); return g; }
    @Override public double gain(double g) { setGain((float) g); this.db = 20.0 * Math.log10(g); return g; }
    @Override public double gain()         { return gain; }

    @Override
    protected float compute(float input, long systemTime) { return input; }
}
