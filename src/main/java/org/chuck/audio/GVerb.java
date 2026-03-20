package org.chuck.audio;

/**
 * GVerb: High-quality studio reverb.
 * Simplified FDN-based implementation.
 */
public class GVerb extends StereoUGen {
    private final Delay[] delays = new Delay[8];
    private float mix = 0.1f;
    private float roomSize = 0.5f;
    private float damping = 0.5f;

    public GVerb(float sampleRate) {
        super();
        // Prime-related delay lengths for richness
        int[] lens = {1117, 1361, 1741, 1997, 2287, 2591, 2851, 3163};
        for (int i = 0; i < 8; i++) {
            delays[i] = new Delay(lens[i], sampleRate);
        }
    }

    public void mix(float m) { this.mix = m; }
    public void roomSize(float s) { this.roomSize = s; }
    public void damping(float d) { this.damping = d; }

    @Override
    protected void computeStereo(float input, long systemTime) {
        float wetL = 0, wetR = 0;
        float feedback = roomSize * 0.95f;
        
        for (int i = 0; i < 8; i++) {
            float out = delays[i].getLastOut();
            // Householder matrix or simple mix?
            // Simple FDN-like feedback:
            float in = input + out * feedback;
            delays[i].tick(in, systemTime);
            
            if (i % 2 == 0) wetL += out;
            else wetR += out;
        }
        
        lastOutLeft = input * (1.0f - mix) + (wetL / 4.0f) * mix;
        lastOutRight = input * (1.0f - mix) + (wetR / 4.0f) * mix;
    }
}
