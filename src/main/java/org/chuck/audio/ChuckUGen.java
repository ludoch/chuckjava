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
    protected final List<ChuckUGen> sources = new CopyOnWriteArrayList<>();
    protected final List<ChuckUGen> targets = new CopyOnWriteArrayList<>();
    protected float lastOut = 0.0f;
    protected float gain = 1.0f;
    protected long lastTickTime = -1;
    protected boolean isTicking = false;
    
    protected int numInputs = 1;
    protected int numOutputs = 1;
    protected ChuckUGen[] inputChannels;
    protected ChuckUGen[] outputChannels;

    public ChuckUGen(ChuckType type) {
        super(type);
    }

    public ChuckUGen() {
        super(new ChuckType("UGen", ChuckType.OBJECT, 0, 0));
    }

    public void addSource(ChuckUGen src) {
        if (src != null && !sources.contains(src)) {
            sources.add(src);
        }
    }

    public void removeSource(ChuckUGen src) {
        sources.remove(src);
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

    public float tick(long systemTime) {
        if (systemTime != -1 && systemTime == lastTickTime) {
            return lastOut;
        }

        if (isTicking) return lastOut;
        isTicking = true;

        try {
            float sum = 0.0f;
            for (ChuckUGen src : sources) {
                sum += src.tick(systemTime);
            }

            lastOut = compute(sum) * gain;
            lastTickTime = systemTime;
            
            return lastOut;
        } finally {
            isTicking = false;
        }
    }

    public float tick() {
        return tick(-1);
    }

    public float tick(float manualInput) {
        lastOut = compute(manualInput) * gain;
        return lastOut;
    }

    protected abstract float compute(float input);

    public void setGain(float gain) {
        this.gain = gain;
    }

    public float getGain() {
        return gain;
    }

    /** ChucK-style gain(val) setter — called as p.gain(0.5) */
    public double gain(double val) {
        this.gain = (float) val;
        return val;
    }

    /** ChucK-style gain() getter — called as p.gain() */
    public double gain() {
        return this.gain;
    }

    public float getLastOut() {
        return lastOut;
    }

    public int getNumSources() {
        return sources.size();
    }

    public int isConnectedTo(ChuckUGen target) {
        return isConnectedTo(target, 0);
    }

    private int isConnectedTo(ChuckUGen target, int depth) {
        if (depth > 5) return 0;
        if (targets.contains(target)) return 1;
        System.err.println("  " + this + " isConnectedTo " + target + " ? targets=" + targets);
        // Check if any of our targets connects to the target (recursive)
        for (ChuckUGen t : targets) {
            if (t.isConnectedTo(target, depth + 1) == 1) return 1;
        }
        // Also check input channels of target
        if (target != null && target.inputChannels != null) {
            for (ChuckUGen in : target.inputChannels) {
                if (in != null && this.isConnectedTo(in, depth + 1) == 1) return 1;
            }
        }
        return 0;
    }

    public void disconnectAll() {
        for (ChuckUGen target : targets) {
            target.removeSource(this);
        }
        targets.clear();
    }

    public void clearSources() {
        for (ChuckUGen src : sources) {
            src.targets.remove(this);
        }
        sources.clear();
    }

    public void tick(float[] buffer) {
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = tick();
        }
    }
    
    public int getNumInputs() { return numInputs; }
    public int getNumOutputs() { return numOutputs; }
    public ChuckUGen getInputChannel(int i) { return (inputChannels != null && i < inputChannels.length) ? inputChannels[i] : this; }
    public ChuckUGen getOutputChannel(int i) { return (outputChannels != null && i < outputChannels.length) ? outputChannels[i] : this; }
}
