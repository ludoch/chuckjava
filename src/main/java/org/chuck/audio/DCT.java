package org.chuck.audio;

/**
 * DCT — Discrete Cosine Transform (Type II) UAna.
 * Accumulates audio samples in a buffer; on upchuck() produces N DCT coefficients.
 * X[k] = sum(n=0..N-1) x[n] * cos(pi * k * (2n+1) / (2N))
 *
 * Usage in ChucK:
 *   adc => DCT dct => blackhole;
 *   dct.size(512);
 *   while (true) { 512::samp => now; dct.upchuck() @=> UAnaBlob blob; }
 */
public class DCT extends UAna {
    private int size;
    private float[] buffer;
    private int pos = 0;

    public DCT() { this(512); }

    public DCT(int size) {
        this.size = size;
        this.buffer = new float[size];
    }

    public void setSize(int n) {
        this.size = n;
        this.buffer = new float[n];
        this.pos = 0;
    }

    public int getSize() { return size; }

    @Override
    protected float compute(float input, long systemTime) {
        buffer[pos % size] = input;
        pos++;
        return input;
    }

    @Override
    protected void computeUAna() {
        int N = size;
        float[] X = new float[N];
        double factor = Math.PI / (2.0 * N);
        for (int k = 0; k < N; k++) {
            double sum = 0;
            for (int n = 0; n < N; n++) {
                sum += buffer[n] * Math.cos(factor * k * (2 * n + 1));
            }
            X[k] = (float) sum;
        }
        lastBlob.setFvals(X);
    }
}
