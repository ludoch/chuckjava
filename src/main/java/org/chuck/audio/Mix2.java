package org.chuck.audio;


/**
 * Mix2: 2-channel stereo mixer.
 */
public class Mix2 extends StereoUGen {
    @Override
    public void tick(float[] buffer, int offset, int length, long systemTime) {
        // Simple pass-through for now, but vectorized
        // Input is already in buffer (sum of all sources)
        
        // Update last outs for scalar callers
        if (length > 0) {
            lastOutChannels[0] = buffer[offset + length - 1];
            lastOutChannels[1] = buffer[offset + length - 1];
            lastTickTime = (systemTime == -1) ? -1 : systemTime + length - 1;
        }
    }

    @Override
    protected void computeStereo(float input, long systemTime) {
        lastOutChannels[0] = input;
        lastOutChannels[1] = input;
    }
}
