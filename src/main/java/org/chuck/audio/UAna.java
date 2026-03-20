package org.chuck.audio;

/**
 * Base class for Unit Analyzers.
 */
public abstract class UAna extends ChuckUGen {
    protected UAnaBlob lastBlob = new UAnaBlob();

    public UAnaBlob upchuck() {
        // Recursively trigger analysis on upstream UAnas
        for (ChuckUGen src : sources) {
            if (src instanceof UAna u) {
                u.upchuck();
            }
        }
        // Trigger this analyzer's calculation
        computeUAna();
        return lastBlob;
    }

    protected abstract void computeUAna();

    @Override
    protected float compute(float input, long systemTime) {
        // UAna usually passes input through to output for monitoring
        return input;
    }

    public UAnaBlob getLastBlob() {
        return lastBlob;
    }
}
