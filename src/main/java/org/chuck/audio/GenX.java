package org.chuck.audio;

/**
 * Base class for GenX family of lookup table Unit Generators.
 * Acts as a wavetable oscillator or a static lookup table.
 */
public abstract class GenX extends ChuckUGen {
    protected float[] table;
    protected double phase = 0.0;
    protected double freq = 0.0; // 0 means it doesn't auto-oscillate by default
    protected final float sampleRate;

    public GenX(float sampleRate) {
        this.sampleRate = sampleRate;
        this.table = new float[4096]; // Standard ChucK GenX size
    }

    public void size(int n) {
        this.table = new float[n];
    }

    public void freq(double f) { this.freq = f; }

    public float lookup(float index) {
        if (table.length == 0) return 0.0f;
        // Normalised lookup (0.0 to 1.0)
        int i = (int)(index * (table.length - 1));
        i = Math.max(0, Math.min(i, table.length - 1));
        return table[i];
    }

    @Override
    protected float compute(float input) {
        if (table.length == 0) return 0.0f;

        if (freq != 0) {
            // Oscillate mode
            int i0 = (int)(phase * table.length);
            int i1 = (i0 + 1) % table.length;
            float frac = (float)(phase * table.length - i0);
            lastOut = table[i0] + (table[i1] - table[i0]) * frac;

            phase += freq / sampleRate;
            while (phase >= 1.0) phase -= 1.0;
            while (phase < 0.0) phase += 1.0;
        } else {
            // Static lookup mode based on input (if input is 0-1)
            int idx = (int)(input * (table.length - 1));
            idx = Math.max(0, Math.min(idx, table.length - 1));
            lastOut = table[idx];
        }
        return lastOut;
    }
}
