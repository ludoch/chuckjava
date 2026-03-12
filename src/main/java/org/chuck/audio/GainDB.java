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
    public double getDb() { return db; }
    public double db()    { return db; }
    public double gain()  { return gain; }

    @Override
    protected float compute(float input) { return input; }
}
