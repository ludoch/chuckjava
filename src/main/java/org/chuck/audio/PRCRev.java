package org.chuck.audio;

/**
 * PRCRev: Perry R. Cook's Reverb.
 */
public class PRCRev extends StereoUGen {
    private final AllPass[] allpass = new AllPass[2];
    private final Comb[] comb = new Comb[2];
    private float mix = 0.5f;

    public PRCRev(float sampleRate) {
        super();
        allpass[0] = new AllPass(353);
        allpass[1] = new AllPass(1097);
        comb[0] = new Comb(1777);
        comb[1] = new Comb(2137);
        
        allpass[0].setCoefficient(0.7f);
        allpass[1].setCoefficient(0.7f);
        comb[0].setCoefficient(0.8f);
        comb[1].setCoefficient(0.8f);
    }

    public void mix(float m) { this.mix = m; }
    public float mix() { return mix; }

    @Override
    protected void computeStereo(float input, long systemTime) {
        float temp = allpass[0].tick(input, systemTime);
        temp = allpass[1].tick(temp, systemTime);
        
        float wet = (comb[0].tick(temp, systemTime) + comb[1].tick(temp, systemTime)) * 0.5f;
        
        lastOutLeft = input * (1.0f - mix) + wet * mix;
        lastOutRight = lastOutLeft;
    }
}
