package org.chuck.audio;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * A Unit Analyzer that captures raw time-domain samples for an Oscilloscope.
 */
public class Scope extends UAna {
    private float[] ring;
    private int writePos = 0;
    private int size;

    public Scope() {
        this(1024);
    }

    public Scope(int size) {
        setSize(size);
    }

    public void setSize(int size) {
        this.size = size;
        this.ring = new float[size];
        this.writePos = 0;
    }

    @Override
    protected float compute(float input) {
        ring[writePos] = input;
        writePos = (writePos + 1) % size;
        return input;
    }

    @Override
    protected void computeUAna() {
        // Copy samples in order (oldest to newest)
        List<Complex> samples = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int idx = (writePos + i) % size;
            samples.add(new Complex(ring[idx], 0));
        }
        lastBlob.setCvals(samples);
    }
}
