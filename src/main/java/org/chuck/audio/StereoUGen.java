package org.chuck.audio;

/**
 * Base class for stereo Unit Generators.
 */
public abstract class StereoUGen extends ChuckUGen {
    protected float lastOutLeft = 0.0f;
    protected float lastOutRight = 0.0f;

    public StereoUGen() {
        super();
        this.numOutputs = 2;
    }

    public float getLastOutLeft() {
        return lastOutLeft;
    }

    public float getLastOutRight() {
        return lastOutRight;
    }

    @Override
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

            computeStereo(sum, systemTime);
            
            // Apply gain to stereo components
            lastOutLeft *= gain;
            lastOutRight *= gain;
            
            // Return left channel as the 'primary' output for mono-listeners
            lastOut = lastOutLeft;
            
            lastTickTime = systemTime;
            
            return lastOut;
        } finally {
            isTicking = false;
        }
    }

    @Override
    public float tick() {
        // Fallback for manual ticks
        return tick(-1);
    }

    @Override
    protected float compute(float input, long systemTime) {
        computeStereo(input, systemTime);
        return (lastOutLeft + lastOutRight) * 0.5f;
    }

    protected abstract void computeStereo(float input, long systemTime);
}
