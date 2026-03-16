package org.chuck.audio;

/**
 * Base class for stereo Unit Generators.
 */
public abstract class StereoUGen extends ChuckUGen {
    protected float lastOutLeft = 0.0f;
    protected float lastOutRight = 0.0f;

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

            computeStereo(sum);
            
            // Apply gain to stereo outputs
            lastOutLeft *= gain;
            lastOutRight *= gain;
            
            // For mono output of a stereo UGen, average the channels
            lastOut = (lastOutLeft + lastOutRight) * 0.5f;
            
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

    protected abstract void computeStereo(float input);
}
