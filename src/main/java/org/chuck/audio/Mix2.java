package org.chuck.audio;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import static org.chuck.audio.VectorAudio.SPECIES;

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
            lastOutLeft = buffer[offset + length - 1];
            lastOutRight = buffer[offset + length - 1];
            lastTickTime = (systemTime == -1) ? -1 : systemTime + length - 1;
        }
    }

    @Override
    protected void computeStereo(float input, long systemTime) {
        lastOutLeft = input;
        lastOutRight = input;
    }
}
