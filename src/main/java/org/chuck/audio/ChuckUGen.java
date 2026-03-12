package org.chuck.audio;

import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * Base class for Unit Generators.
 */
public abstract class ChuckUGen extends ChuckObject {
    protected float lastOut = 0.0f;
    protected float gain = 1.0f;
    protected final List<ChuckUGen> sources = new CopyOnWriteArrayList<>();
    protected final List<ChuckUGen> targets = new CopyOnWriteArrayList<>();
    
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
        if (target != null) {
            target.addSource(this);
            if (!targets.contains(target)) {
                targets.add(target);
            }
        }
    }

    public void unchuck(ChuckUGen target) {
        if (target != null) {
            target.removeSource(this);
            targets.remove(target);
        }
    }

    /** Disconnect this UGen from all downstream targets. */
    public void disconnectAll() {
        for (ChuckUGen target : new ArrayList<>(targets)) {
            target.removeSource(this);
        }
        targets.clear();
    }

    public void addSource(ChuckUGen src) {
        if (src != null && !sources.contains(src)) {
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

    /** ChucK-style method call: osc.gain(0.5) */
    public float gain(float g) {
        this.gain = g;
        return g;
    }

    public float getGain() {
        return gain;
    }

    @Override
    protected void triggerDataHook(int index, long value) {
        if (index == 1) { // gain
            this.gain = (float) getDataAsDouble(1);
        }
    }

    @Override
    public void setData(int index, long value) {
        super.setData(index, value);
        // The base class super.setData(index, value) sets isDouble[index] = false
        // Then we trigger the hook.
        triggerDataHook(index, value);
    }

    @Override
    public void setData(int index, double value) {
        super.setData(index, value);
        // The base class super.setData(index, double) sets isDouble[index] = true
        // Then we trigger the hook.
        triggerDataHook(index, Double.doubleToRawLongBits(value));
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
