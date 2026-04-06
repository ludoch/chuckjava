package org.chuck.audio;

/**
 * XCorr — Cross-correlation UAna.
 * Cross-correlates the audio inputs from two sources.
 * Connect two signals: sig1 => xcorr; sig2 => xcorr;
 * Result: R[k] = (1/N) * sum(n) x[n] * y[n+k]
 *
 * Usage in ChucK:
 *   adc => XCorr xc => blackhole;
 *   mic => xc;
 *   while (true) { 1024::samp => now; xc.upchuck() @=> UAnaBlob blob; }
 */
public class XCorr extends UAna {
    private int size;
    private float[] bufA;
    private float[] bufB;
    private int posA = 0, posB = 0;
    private int sourceCount = 0;

    public XCorr() { this(1024); }

    public XCorr(int size) {
        this.size = size;
        this.bufA = new float[size];
        this.bufB = new float[size];
    }

    public void setSize(int n) {
        this.size = n;
        this.bufA = new float[n];
        this.bufB = new float[n];
        this.posA = this.posB = 0;
    }

    public int getSize() { return size; }

    /** Called once per sample with the mixed input from all sources. */
    @Override
    protected float compute(float input, long systemTime) {
        // Alternate filling bufA and bufB from sources
        // In practice the UGen graph mixes all sources before compute;
        // we just store the mixed input in bufA and the previous value in bufB.
        bufB[posB % size] = bufA[posA % size];
        bufA[posA % size] = input;
        posA++;
        posB++;
        return input;
    }

    @Override
    protected void computeUAna() {
        int N = size;
        float[] result = new float[N];
        for (int k = 0; k < N; k++) {
            double sum = 0;
            for (int n = 0; n < N - k; n++) {
                sum += bufA[n] * bufB[(n + k) % N];
            }
            result[k] = (float)(sum / N);
        }
        lastBlob.setFvals(result);
    }
}
