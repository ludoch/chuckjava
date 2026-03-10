package org.chuck.audio;

/**
 * A Unit Generator for sample playback.
 * Loads a float array of samples and plays them back at a given rate.
 */
public class SndBuf extends ChuckUGen {
    private float[] samples;
    private double pos = 0.0;
    private double rate = 1.0;
    private boolean loop = false;

    public SndBuf() {
        this.samples = new float[0];
    }

    public void setSamples(float[] samples) {
        this.samples = samples;
        this.pos = 0;
    }

    public void setRate(double rate) {
        this.rate = rate;
    }

    public void setPos(double pos) {
        this.pos = pos;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    @Override
    protected float compute(float input) {
        if (samples.length == 0 || pos >= samples.length || pos < 0) {
            if (loop && samples.length > 0) {
                pos = pos % samples.length;
                if (pos < 0) pos += samples.length;
            } else {
                return 0.0f;
            }
        }

        // Linear interpolation
        int i0 = (int) pos;
        int i1 = (i0 + 1) % samples.length;
        float frac = (float) (pos - i0);
        
        float s0 = samples[i0];
        float s1 = samples[i1];
        float out = s0 + (s1 - s0) * frac;

        pos += rate;
        
        return out;
    }

    public boolean isDone() {
        return !loop && (pos >= samples.length || pos < 0);
    }
}
