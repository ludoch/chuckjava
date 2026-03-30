package org.chuck.audio;

/**
 * MixN: N-channel mixer.
 */
public class MixN extends MultiChannelUGen {
    public MixN(int numChannels) {
        super(numChannels);
    }

    @Override
    protected void computeMulti(float input, long systemTime) {
        for (int i = 0; i < lastOutChannels.length; i++) {
            lastOutChannels[i] = input;
        }
    }
}
