package org.chuck.audio;

/**
 * AutoCorr — Autocorrelation UAna.
 * Accumulates audio in a buffer; on upchuck() computes normalized autocorrelation.
 * Useful for pitch detection and periodicity analysis.
 *
 * Result: R[k] = (1/N) * sum(n=0..N-k-1) x[n] * x[n+k], k = 0..N-1
 * R[0] = 1.0 when normalized (divide all by R[0]).
 *
 * Usage in ChucK:
 *   adc => AutoCorr ac => blackhole;
 *   while (true) { 1024::samp => now; ac.upchuck() @=> UAnaBlob blob; }
 */
public class AutoCorr extends UAna {
    private int size;
    private float[] buffer;
    private int pos = 0;

    public AutoCorr() { this(1024); }

    public AutoCorr(int size) {
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
        float[] result = new float[N];
        // Direct method: O(N²) — acceptable for typical frame sizes ≤ 2048
        for (int k = 0; k < N; k++) {
            double sum = 0;
            for (int n = 0; n < N - k; n++) {
                sum += buffer[n] * buffer[n + k];
            }
            result[k] = (float)(sum / N);
        }
        // Normalize so result[0] = 1.0
        if (result[0] > 1e-10f) {
            float norm = result[0];
            for (int k = 0; k < N; k++) result[k] /= norm;
        }
        lastBlob.setFvals(result);
    }
}
