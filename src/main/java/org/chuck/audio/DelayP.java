package org.chuck.audio;

/**
 * Pitch-shifting delay using two crossfading read-heads.
 * Compatible with the ChucK DelayP API: delay(samples), max(samples), shift(semitones).
 */
public class DelayP extends ChuckUGen {
    private float[] buffer;
    private int writePos  = 0;
    private int maxSamples;
    private double delaySamples;
    private double shiftRate = 1.0; // playback speed (1.0 = no shift)
    private double readA, readB;
    private int xfadeLen;
    private int xfadePos = 0;
    private static final int XFADE_SAMPLES = 256;

    public DelayP(int maxDelaySamples, float sampleRate) {
        this.maxSamples  = Math.max(maxDelaySamples, 2048);
        this.buffer      = new float[this.maxSamples + 4];
        this.delaySamples = maxDelaySamples - 1;
        this.readA        = 0;
        this.readB        = this.maxSamples / 2.0;
        this.xfadeLen     = XFADE_SAMPLES;
    }

    /** Set delay in samples */
    public void delay(double samples) {
        if (samples < 0) samples = 0;
        if (samples >= maxSamples) samples = maxSamples - 1;
        delaySamples = samples;
    }
    public double delay() { return delaySamples; }

    /** Set max delay (resizes buffer) */
    public void max(double samples) {
        int n = (int) Math.max(samples, 256);
        buffer = new float[n + 4];
        maxSamples = n;
    }

    /** shift in semitones (negative = lower, positive = higher) */
    public void shift(double semitones) {
        shiftRate = Math.pow(2.0, semitones / 12.0);
    }
    public double shift() { return 12.0 * Math.log(shiftRate) / Math.log(2.0); }

    @Override
    protected float compute(float input, long systemTime) {
        // write
        buffer[writePos] = input;
        writePos = (writePos + 1) % maxSamples;

        // advance read heads at shiftRate
        readA += shiftRate;
        readB += shiftRate;

        // wrap
        while (readA >= maxSamples) readA -= maxSamples;
        while (readA <  0)          readA += maxSamples;
        while (readB >= maxSamples) readB -= maxSamples;
        while (readB <  0)          readB += maxSamples;

        // crossfade weight for head B (triangular XFade)
        xfadePos = (xfadePos + 1) % (maxSamples / 2);
        double t = (double) xfadePos / (maxSamples / 2.0);
        double wA = Math.abs(1.0 - 2.0 * t);
        double wB = 1.0 - wA;

        return (float)(wA * lerp(readA) + wB * lerp(readB));
    }

    private float lerp(double pos) {
        int i0 = (int) pos % maxSamples;
        int i1 = (i0 + 1) % maxSamples;
        float frac = (float)(pos - Math.floor(pos));
        return buffer[i0] + (buffer[i1] - buffer[i0]) * frac;
    }
}
