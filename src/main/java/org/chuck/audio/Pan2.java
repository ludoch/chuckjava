package org.chuck.audio;

/**
 * A mono-to-stereo unit generator for stereo panning.
 */
public class Pan2 extends StereoUGen {
    private float pan = 0.0f; // -1 (left) to 1 (right)
    private int panType = 1; // 0: linear, 1: constant power

    public void setPan(float pan) {
        this.pan = pan;
    }

    public float getPan() {
        return pan;
    }

    public void setPanType(int type) {
        this.panType = type;
    }

    @Override
    protected void computeStereo(float input) {
        if (panType == 1) { // Constant power
            double angle = (pan + 1.0) * (Math.PI / 4.0);
            lastOutLeft = (float) (input * Math.cos(angle));
            lastOutRight = (float) (input * Math.sin(angle));
        } else { // Linear
            lastOutLeft = input * (1.0f - pan) * 0.5f;
            lastOutRight = input * (pan + 1.0f) * 0.5f;
        }
    }

    @Override
    protected float compute(float input) {
        // Not used, computeStereo is used instead
        return 0.0f;
    }
}
