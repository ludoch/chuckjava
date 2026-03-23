package org.chuck.audio;

/**
 * A delay-line UGen.
 */
public class Delay extends ChuckUGen {
    private final float[] buffer;
    private int writePos = 0;
    private int delaySamples;
    @SuppressWarnings("unused")
    private final float sampleRate;

    public Delay(int maxDelaySamples) {
        this(maxDelaySamples, 44100.0f);
    }

    public Delay(int maxDelaySamples, float sampleRate) {
        this.buffer = new float[maxDelaySamples];
        this.delaySamples = 0; // Default to 0 delay
        this.sampleRate = sampleRate;
    }

    public void setDelay(int samples) {
        if (samples >= buffer.length) samples = buffer.length - 1;
        this.delaySamples = samples;
    }

    public void setDelay(double samples) {
        setDelay((int) samples);
    }

    public double delay() { return (double) delaySamples; }
    public double getDelay() { return (double) delaySamples; }

    public void init(double delay, double max) {
        // We can't easily resize the buffer here if it's already allocated, 
        // but ChucK's 'Delay delay(dur, dur)' sets both.
        // For now, we assume max was already used in constructor or we just set delay.
        setDelay(delay);
    }

    @Override
    protected float compute(float input, long systemTime) {
        if (systemTime != -1 && systemTime == lastTickTime) {
            return lastOut;
        }
        
        // Read delayed sample
        int readPos = (writePos - delaySamples + buffer.length) % buffer.length;
        float out = buffer[readPos];

        // Write current input to buffer
        buffer[writePos] = input;
        writePos = (writePos + 1) % buffer.length;

        lastTickTime = systemTime;
        lastOut = out;
        return out;
    }
}
