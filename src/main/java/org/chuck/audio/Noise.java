package org.chuck.audio;

import java.util.Random;

/**
 * A white noise generator UGen.
 */
public class Noise extends ChuckUGen {
    private final Random random = new Random();

    @Override
    protected float compute(float input, long systemTime) {
        // Generate a random float between -1.0 and 1.0
        return input + (random.nextFloat() * 2.0f - 1.0f);
    }
}
