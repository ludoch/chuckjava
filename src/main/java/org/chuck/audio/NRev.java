package org.chuck.audio;

/**
 * NRev: Classic STK Reverb.
 */
public class NRev extends StereoUGen {
    private final AllPass[] allpass = new AllPass[8];
    private float mix = 0.5f;

    public NRev(float sampleRate) {
        super();
        allpass[0] = new AllPass(143);
        allpass[1] = new AllPass(241);
        allpass[2] = new AllPass(391);
        allpass[3] = new AllPass(511);
        allpass[4] = new AllPass(1021);
        allpass[5] = new AllPass(1733);
        allpass[6] = new AllPass(2511);
        allpass[7] = new AllPass(3539);
        
        for (int i = 0; i < 8; i++) allpass[i].setCoefficient(0.7f);
    }

    public void mix(float m) { this.mix = m; }
    public float mix() { return mix; }

    @Override
    protected void computeStereo(float input, long systemTime) {
        float temp = input;
        for (int i = 0; i < 8; i++) {
            temp = allpass[i].tick(temp, systemTime);
        }
        
        lastOutLeft = input * (1.0f - mix) + temp * mix;
        lastOutRight = lastOutLeft; // NRev is mono-in, quasi-mono out in this simple version
    }
}
