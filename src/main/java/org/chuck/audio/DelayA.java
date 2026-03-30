package org.chuck.audio;

/**
 * Allpass-interpolating delay line.
 *
 * Provides fractional delay with flat phase response (unlike DelayL's linear
 * interpolation which has non-flat phase). Uses a first-order allpass filter:
 *   y[n] = -c * y[n-1] + x[n-D] + c * x[n-D-1]
 * where c = (1 - alpha) / (1 + alpha) and alpha is the fractional delay part
 * constrained to [0.5, 1.5] for optimal phase behaviour.
 */
public class DelayA extends ChuckUGen {
    private final float[] buffer;
    private int writePos = 0;
    private int outPoint = 0;

    private double delay     = 0.5;
    private double alpha     = 0.5;   // fractional part in [0.5, 1.5]
    private double coeff     = 0.0;   // allpass coeff = (1-alpha)/(1+alpha)
    private double apInput   = 0.0;   // allpass filter input state
    private double apOutput  = 0.0;   // allpass filter output state (y[n-1])
    private boolean needNext = true;
    private double nextOut   = 0.0;

    private final float sampleRate;

    public DelayA(int maxDelaySamples, float sampleRate) {
        this.buffer     = new float[maxDelaySamples + 2];
        this.sampleRate = sampleRate;
        setDelay(0.5);
    }

    public DelayA(int maxDelaySamples) {
        this(maxDelaySamples, 44100.0f);
    }

    public void setDelay(double samples) {
        int len = buffer.length;
        if (samples > len - 1) samples = len - 1;
        if (samples < 0.5)    samples = 0.5;
        delay = samples;

        double outPointer = writePos - samples + 1.0;
        while (outPointer < 0) outPointer += len;

        outPoint = (int) outPointer;
        alpha    = 1.0 + outPoint - outPointer; // fractional remainder

        // Keep alpha in [0.5, 1.5] for best phase flatness
        if (alpha < 0.5) {
            outPoint = (outPoint + 1) % len;
            alpha += 1.0;
        }
        coeff = (1.0 - alpha) / (1.0 + alpha);
        needNext = true;
    }

    public double delay()    { return delay; }
    public double getDelay() { return delay; }

    // ChucK-style: max / delay in seconds
    public void setDelaySec(double sec) { setDelay(sec * sampleRate); }

    private double computeNext() {
        return -coeff * apOutput + apInput + coeff * buffer[outPoint];
    }

    @Override
    protected float compute(float input, long systemTime) {
        int len = buffer.length;

        // Write input
        buffer[writePos] = input;
        writePos = (writePos + 1) % len;

        // Get allpass-interpolated output
        if (needNext) nextOut = computeNext();
        apOutput = nextOut;
        needNext = true;

        // Save the integer-sample input for next allpass computation
        apInput = buffer[outPoint];
        outPoint = (outPoint + 1) % len;

        // Recompute for caching
        nextOut  = computeNext();
        needNext = false;

        return (float) apOutput;
    }
}
