package org.chuck.audio;

import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for Unit Generators.
 */
public abstract class ChuckUGen extends ChuckObject {
    protected float lastOut = 0.0f;
    protected float gain = 1.0f;
    protected final List<ChuckUGen> sources = new ArrayList<>();
    
    // The last logical time (in samples) this UGen was ticked.
    protected long lastTickTime = -1;
    // Internal counter for manual ticks (unit tests)
    private long manualTickCount = 0;

    public ChuckUGen() {
        super(new ChuckType("UGen", ChuckType.OBJECT, 8, 0));
    }

    public ChuckUGen(ChuckType type) {
        super(type);
    }

    public void chuckTo(ChuckUGen target) {
        target.addSource(this);
    }

    public void unchuck(ChuckUGen target) {
        target.removeSource(this);
    }

    public void addSource(ChuckUGen src) {

        if (!sources.contains(src)) {
            sources.add(src);
        }
    }

    /** Remove a specific upstream connection. */
    public void removeSource(ChuckUGen src) {
        sources.remove(src);
    }

    /** Remove all upstream connections (used by VM clear to silence the DAC). */
    public void clearSources() {
        sources.clear();
    }

    public void setGain(float gain) {
        this.gain = gain;
    }

    public float getGain() {
        return gain;
    }

    @Override
    public void setData(int index, long value) {
        if (index == 1) { // gain
            this.gain = (float) Double.longBitsToDouble(value);
        }
        super.setData(index, value);
    }

    /**
     * Pull-based ticking mechanism. 
     */
    public float tick(long systemTime) {
        if (systemTime != -1 && systemTime == lastTickTime) {
            return lastOut;
        }

        float sum = 0.0f;
        for (ChuckUGen src : sources) {
            sum += src.tick(systemTime);
        }

        lastOut = compute(sum) * gain;
        lastTickTime = systemTime;
        
        return lastOut;
    }

    /**
     * For manual processing (not driven by pull).
     * Uses a dedicated counter to ensure unit tests are deterministic.
     */
    public float tick() {
        return tick(manualTickCount++);
    }

    /**
     * Convenience method for testing or manual processing with specific input.
     */
    public float tick(float input) {
        lastOut = compute(input) * gain;
        // Don't update lastTickTime here to not interfere with pull-based graph
        return lastOut;
    }

    protected abstract float compute(float input);

    public float getLastOut() {
        return lastOut;
    }

    public int getNumSources() {
        return sources.size();
    }

    public void tick(float[] buffer) {
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = tick();
        }
    }
}
