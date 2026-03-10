package org.chuck.audio;

/**
 * John Chowning Reverb.
 */
public class JCRev extends StereoUGen {
    private final AllPass[] allpass = new AllPass[3];
    private final Comb[] comb = new Comb[4];
    private final Delay outLeft, outRight;
    private float mix = 0.5f;

    public JCRev(float sampleRate) {
        // Delay lengths adapted for 44.1kHz
        allpass[0] = new AllPass(225);
        allpass[1] = new AllPass(556);
        allpass[2] = new AllPass(441);
        
        comb[0] = new Comb(1116);
        comb[1] = new Comb(1356);
        comb[2] = new Comb(1422);
        comb[3] = new Comb(1617);
        
        comb[0].setCoefficient(0.891f);
        comb[1].setCoefficient(0.863f);
        comb[2].setCoefficient(0.841f);
        comb[3].setCoefficient(0.822f);
        
        outLeft = new Delay(100); // Tiny decorrelation
        outRight = new Delay(100);
    }

    public void setMix(float mix) {
        this.mix = mix;
    }

    @Override
    protected void computeStereo(float input) {
        float temp = input;
        for (int i = 0; i < 3; i++) {
            temp = allpass[i].tick(temp);
        }
        
        float filtout = 0;
        for (int i = 0; i < 4; i++) {
            filtout += comb[i].tick(temp);
        }
        
        float dry = input;
        float wetL = outLeft.tick(filtout);
        float wetR = outRight.tick(filtout);
        
        lastOutLeft = dry * (1.0f - mix) + wetL * mix;
        lastOutRight = dry * (1.0f - mix) + wetR * mix;
    }

    @Override
    protected float compute(float input) {
        return 0;
    }
}
