package org.chuck.audio;

/**
 * A basic Dynamics Processor stub.
 */
public class Dyno extends ChuckUGen {
    private float attackTime = 0.005f;
    private float releaseTime = 0.05f;
    private float thresh = 0.5f;
    private float ratio = 1.0f;
    private float slopeAbove = 1.0f;
    private float slopeBelow = 1.0f;

    @Override
    protected float compute(float input) {
        return input; // pass-through stub
    }

    public double attackTime() { return attackTime * 44100.0; } // Assuming 44100
    public void attackTime(double samples) { this.attackTime = (float)(samples / 44100.0); }
    
    public double releaseTime() { return releaseTime * 44100.0; }
    public void releaseTime(double samples) { this.releaseTime = (float)(samples / 44100.0); }

    public double thresh() { return thresh; }
    public void thresh(double v) { thresh = (float)v; }

    public double ratio() { return ratio; }
    public void ratio(double v) { ratio = (float)v; }

    public double slopeAbove() { return slopeAbove; }
    public void slopeAbove(double v) { slopeAbove = (float)v; }

    public double slopeBelow() { return slopeBelow; }
    public void slopeBelow(double v) { slopeBelow = (float)v; }
}
