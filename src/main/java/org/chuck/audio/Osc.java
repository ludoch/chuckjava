package org.chuck.audio;

import org.chuck.core.ChuckType;

/**
 * Base class for simple oscillator unit generators.
 */
public abstract class Osc extends ChuckUGen {
    protected double freq = 220.0;
    protected double phase = 0.0;
    protected double width = 0.5;
    protected int sync = 0; // 0: sync freq, 1: sync phase, 2: FM
    protected final float sampleRate;

    public Osc(float sampleRate) {
        super(new ChuckType("Osc", ChuckType.OBJECT, 8, 0));
        this.sampleRate = sampleRate;
    }

    public void setFreq(double freq) {
        this.freq = freq;
    }

    /** ChucK-style method call: osc.freq(440) */
    public double freq(double f) {
        this.freq = f;
        return f;
    }

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

    public int getSync() {
        return sync;
    }

    /** ChucK-style: osc.last() returns most recent sample */
    public float last() {
        return lastOut;
    }

    @Override
    protected void triggerDataHook(int index, long value) {
        super.triggerDataHook(index, value);
        if (index == 0) { // freq
            this.freq = getDataAsDouble(0);
        } else if (index == 2) { // width
            this.width = getDataAsDouble(2);
        } else if (index == 3) { // phase
            this.phase = getDataAsDouble(3) % 1.0;
            if (this.phase < 0) this.phase += 1.0;
        }
    }

    @Override
    public void setData(int index, long value) {
        super.setData(index, value);
    }

    @Override
    public float tick(long systemTime) {
        if (systemTime != -1 && systemTime == lastTickTime) {
            return lastOut;
        }

        float in = 0.0f;
        for (ChuckUGen src : sources) {
            in += src.tick(systemTime);
        }

        boolean incPhase = true;
        double effectiveFreq = freq;
        if (getNumSources() > 0) {
            if (sync == 0) {
                // Input IS the frequency (audio-rate freq control); don't clobber stored freq
                effectiveFreq = in;
            } else if (sync == 1) {
                phase = in % 1.0;
                if (phase < 0) phase += 1.0;
                incPhase = false;
            } else if (sync == 2) {
                // FM: add input to base freq
                effectiveFreq = freq + in;
            }
        }

        if (incPhase) {
            phase += effectiveFreq / sampleRate;
        }
        
        phase = phase % 1.0;
        if (phase < 0) phase += 1.0;

        lastOut = (float) (computeOsc(phase) * gain);
        lastTickTime = systemTime;
        
        return lastOut;
    }

    @Override
    protected float compute(float input) {
        return 0.0f;
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
            t /= dt;
            return t + t - t * t - 1.0;
        } else if (t > 1.0 - dt) {
            // Just before the discontinuity
            t = (t - 1.0) / dt;
            return t * t + t + t + 1.0;
        }
        return 0.0;
    }
}
