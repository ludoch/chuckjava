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
        super(new ChuckType("Osc", ChuckType.OBJECT, 2, 0));
        this.sampleRate = sampleRate;
    }

    public void setFreq(double freq) {
        this.freq = freq;
    }

    public double getFreq() {
        return freq;
    }

    public void setPhase(double phase) {
        this.phase = phase % 1.0;
        if (this.phase < 0) this.phase += 1.0;
    }

    public double getPhase() {
        return phase;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getWidth() {
        return width;
    }

    public void setSync(int sync) {
        this.sync = sync;
    }

    public int getSync() {
        return sync;
    }

    @Override
    public void setData(int index, long value) {
        super.setData(index, value);
        if (index == 0) { // freq
            this.freq = getDataAsDouble(0);
        } else if (index == 1) { // width
            this.width = getDataAsDouble(1);
        }
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
        if (getNumSources() > 0) {
            if (sync == 0) { 
                freq = in;
            } else if (sync == 1) { 
                phase = in % 1.0;
                if (phase < 0) phase += 1.0;
                incPhase = false;
            } else if (sync == 2) { 
                double currentFreq = freq + in;
                phase += currentFreq / sampleRate;
                incPhase = false;
            }
        }

        if (incPhase) {
            phase += freq / sampleRate;
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
}
