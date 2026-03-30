package org.chuck.audio;

/**
 * Mix16: 16-channel mixer.
 */
public class Mix16 extends MultiChannelUGen {
    public Mix16() {
        super(16);
    }

    @Override
    protected void computeMulti(float input, long systemTime) {
        for (int i = 0; i < 16; i++) {
            lastOutChannels[i] = input;
        }
    }
}
