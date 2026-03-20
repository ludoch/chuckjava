package org.chuck.audio;

/**
 * Mix2: 2-channel stereo mixer.
 */
public class Mix2 extends StereoUGen {
    @Override
    protected void computeStereo(float input, long systemTime) {
        // If input is mono, it goes to both. 
        // If input is stereo (from StereoUGen), it's already handled by sum logic?
        // Wait, StereoUGen tick calls computeStereo(sum, systemTime).
        
        // Simple pass-through for now, StereoUGen handles the channels.
        lastOutLeft = input;
        lastOutRight = input;
    }
}
