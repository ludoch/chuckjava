package org.chuck.audio;

import java.util.ArrayList;
import java.util.List;

/**
 * Fast Fourier Transform Unit Analyzer.
 */
public class FFT extends UAna {
    private int size = 1024;
    private float[] windowBuffer;
    private int bufferIndex = 0;

    public FFT(int size) {
        this.size = size;
        this.windowBuffer = new float[size];
    }

    public void setSize(int size) {
        this.size = size;
        this.windowBuffer = new float[size];
        this.bufferIndex = 0;
    }

    @Override
    protected float compute(float input) {
        // Accumulate samples for analysis
        windowBuffer[bufferIndex] = input;
        bufferIndex = (bufferIndex + 1) % size;
        return super.compute(input);
    }

    @Override
    protected void computeUAna() {
        // Placeholder for real FFT logic (e.g., using Cooley-Tukey or a library)
        List<Complex> spectrum = new ArrayList<>();
        for (int i = 0; i < size / 2; i++) {
            // Mock spectrum data
            spectrum.add(new Complex(0.0f, 0.0f));
        }
        lastBlob.setCvals(spectrum);
    }
}
