package org.chuck.audio;

/**
 * Base class for Unit Analyzers.
 */
public abstract class UAna extends ChuckUGen {
    protected UAnaBlob lastBlob = new UAnaBlob();

    public UAnaBlob upchuck() {
        // Trigger analysis
        computeUAna();
        return lastBlob;
    }

    protected abstract void computeUAna();

    @Override
    protected float compute(float input) {
        // UAna usually passes input through to output for monitoring
        return input;
    }

    public UAnaBlob getLastBlob() {
        return lastBlob;
    }
}
