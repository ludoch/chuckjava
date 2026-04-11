package org.chuck.audio;

import org.chuck.core.ChuckType;

/**
 * Base class for simple oscillator unit generators.
 */
public abstract class Osc extends ChuckUGen {
    @SuppressWarnings("unused") // Used via introspection in ChucK scripts
    protected double freq = 220.0;
    @SuppressWarnings("unused") // Used via introspection in ChucK scripts
    protected double phase = 0.0;
    @SuppressWarnings("unused") // Used via introspection in ChucK scripts
    protected double width = 0.5;
    @SuppressWarnings("unused") // Used via introspection in ChucK scripts
    protected int sync = 0; // 0: sync freq, 1: sync phase, 2: FM
    protected final float sampleRate;

    public Osc(float sampleRate) {
        super(new ChuckType("Osc", ChuckType.OBJECT, 8, 0));
        this.sampleRate = sampleRate;
        // Default freq = 220.0 (index 0)
        setData(0, 220.0);
    }

    public void setFreq(double freq) {
        this.freq = freq;
    }

    /** ChucK-style method call: osc.freq(440) */
    public double freq(double f) {
        this.freq = f;
        return f;
    }

    public double freq() { return freq; }

    public double getFreq() {
        return freq;
    }

    public void setPhase(double phase) {
        this.phase = phase % 1.0;
        if (this.phase < 0) this.phase += 1.0;
    }

    /** ChucK-style method call: osc.phase(0.5) */
    public double phase(double p) {
        setPhase(p);
        return this.phase;
    }

    public double phase() { return phase; }

    public double getPhase() {
        return phase;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    /** ChucK-style method call: osc.width(0.5) */
    public double width(double w) {
        this.width = w;
        return w;
    }

    public double width() { return width; }

    public double getWidth() {
        return width;
    }

    public void setSync(int sync) {
        this.sync = sync;
    }

    /** ChucK-style method call: osc.sync(2) */
    public int sync(int s) {
        this.sync = s;
        return s;
    }

    public int sync() { return sync; }

    public int getSync() {
        return sync;
    }


    public void init(double f) {
        setFreq(f);
    }

    /** ChucK-style: osc.last() returns most recent sample */
    public float last() {
        return lastOut;
    }

    @Override
    protected void triggerDataHook(int index, long value) {
        super.triggerDataHook(index, value);
        switch (index) {
            case 0 -> this.freq = getDataAsDouble(0);
            case 2 -> this.width = getDataAsDouble(2);
            case 3 -> {
                this.phase = getDataAsDouble(3) % 1.0;
                if (this.phase < 0) this.phase += 1.0;
            }
            default -> {}
        }
    }

    @Override
    public void setData(int index, long value) {
        super.setData(index, value);
    }

    @Override
    protected float compute(float in, long systemTime) {
        boolean incPhase = true;
        double effectiveFreq = freq;
        if (getNumSources() > 0) {
            switch (sync) {
                case 0 -> effectiveFreq = in;
                case 1 -> {
                    phase = in % 1.0;
                    if (phase < 0) phase += 1.0;
                    incPhase = false;
                }
                case 2 -> effectiveFreq = freq + in;
                default -> {}
            }
        }

        if (incPhase) {
            phase += effectiveFreq / sampleRate;
        }
        
        phase = phase % 1.0;
        if (phase < 0) phase += 1.0;

        return (float) computeOsc(phase);
    }

    protected abstract double computeOsc(double phase);

    /**
     * PolyBLEP residual correction for a single discontinuity.
     * Apply at every phase wrap-around / edge to reduce aliasing.
     *
     * @param t  normalised phase of the discontinuity (0–1)
     * @param dt phase increment per sample (freq / sampleRate)
     * @return   correction value to add to (or subtract from) the naive waveform
     */
    protected static double polyBlep(double t, double dt) {
        if (dt <= 0.0) return 0.0;
        if (t < dt) {
            // Just after the discontinuity
            double t1 = t / dt;
            return t1 + t1 - t1 * t1 - 1.0;
        } else if (t > 1.0 - dt) {
            // Just before the discontinuity
            double t1 = (t - 1.0) / dt;
            return t1 * t1 + t1 + t1 + 1.0;
        }
        return 0.0;
    }
}
