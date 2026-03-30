package org.chuck.audio;

/**
 * Mix8: 8-channel mixer.
 */
public class Mix8 extends MultiChannelUGen {
    public Mix8() {
        super(8);
    }

    @Override
    protected void computeMulti(float input, long systemTime) {
        for (int i = 0; i < 8; i++) {
            lastOutChannels[i] = input;
        }
    }
}
