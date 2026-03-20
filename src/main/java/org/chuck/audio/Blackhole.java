package org.chuck.audio;

/**
 * A blackhole UGen. Ticks all its inputs but discards the output.
 */
public class Blackhole extends ChuckUGen {
    @Override
    protected float compute(float input, long systemTime) {
        return 0.0f;
    }
}
