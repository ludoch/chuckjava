package org.chuck.audio;

/**
 * A delay-line UGen.
 */
public class Delay extends ChuckUGen {
    private final float[] buffer;
    private int writePos = 0;
    private int delaySamples;

    public Delay(int maxDelaySamples) {
        this.buffer = new float[maxDelaySamples];
        this.delaySamples = maxDelaySamples - 1;
    }

    public void setDelay(int samples) {
        if (samples >= buffer.length) samples = buffer.length - 1;
        this.delaySamples = samples;
    }

    @Override
    protected float compute(float input) {
        // Read delayed sample
        int readPos = (writePos - delaySamples + buffer.length) % buffer.length;
        float out = buffer[readPos];

        // Write current input to buffer
        buffer[writePos] = input;
        writePos = (writePos + 1) % buffer.length;

        return out;
    }
}
