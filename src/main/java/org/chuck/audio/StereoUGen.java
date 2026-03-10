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
    public float tick() {
        // For stereo UGens, tick returns the average or mono-downmix by default,
        // but populates lastOutLeft and lastOutRight.
        float in = 0.0f;
        for (ChuckUGen src : sources) {
            in += src.getLastOut();
        }
        computeStereo(in);
        lastOut = (lastOutLeft + lastOutRight) * 0.5f * gain;
        return lastOut;
    }

    protected abstract void computeStereo(float input);
}
