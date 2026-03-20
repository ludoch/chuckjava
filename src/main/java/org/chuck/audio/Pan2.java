package org.chuck.audio;

import org.chuck.core.ChuckType;

/**
 * A mono-to-stereo unit generator for stereo panning.
 */
public class Pan2 extends StereoUGen {
    public final Gain left = new Gain();
    public final Gain right = new Gain();
    private float pan = 0.0f; // -1 (left) to 1 (right)
    private int panType = 1; // 0: linear, 1: constant power

    public Pan2() {
        this.numInputs = 2;
        this.numOutputs = 2;
        this.inputChannels = new ChuckUGen[]{ new Gain(), new Gain() };
        this.outputChannels = new ChuckUGen[]{ left, right };
        
        // Internal routing for connection tracking
        inputChannels[0].chuckTo(left);
        inputChannels[1].chuckTo(right);
        
        // Also connect to main processing for ticking
        inputChannels[0].chuckTo(this);
        inputChannels[1].chuckTo(this);
    }

    public void setPan(float pan) {
        this.pan = pan;
    }

    public float getPan() {
        return pan;
    }

    /** ChucK-style: p.pan(0.5) */
    public float pan(float p) {
        this.pan = p;
        return p;
    }

    /** ChucK-style: p.pan() */
    public float pan() {
        return pan;
    }

    public void setPanType(int type) {
        this.panType = type;
    }

    /** ChucK-style: p.panType(1) */
    public int panType(int t) {
        this.panType = t;
        return t;
    }

    @Override
    protected void computeStereo(float input, long systemTime) {
        // We need to handle individual inputs for true stereo-to-stereo
        float in0 = inputChannels[0].lastOut;
        float in1 = inputChannels[1].lastOut;
        
        // Simplified: if only one input is connected, use it as mono
        // If two are connected, treat as stereo
        float monoInput = input; 
        
        if (panType == 1) { // Constant power
            double angle = (pan + 1.0) * (Math.PI / 4.0);
            lastOutLeft = (float) (monoInput * Math.cos(angle));
            lastOutRight = (float) (monoInput * Math.sin(angle));
        } else { // Linear
            lastOutLeft = monoInput * (1.0f - pan) * 0.5f;
            lastOutRight = monoInput * (pan + 1.0f) * 0.5f;
        }
        // Send to output proxies
        left.lastOut = lastOutLeft;
        right.lastOut = lastOutRight;
    }
}
