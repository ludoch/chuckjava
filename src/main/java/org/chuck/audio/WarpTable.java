package org.chuck.audio;

/**
 * WarpTable: Advanced lookup table with index warping.
 */
public class WarpTable extends GenX {
    private float warp = 1.0f;

    public WarpTable(float sampleRate) {
        super(sampleRate);
    }

    public void warp(float w) { this.warp = w; }
    public float warp() { return warp; }

    @Override
    protected float compute(float input, long systemTime) {
        if (table.length == 0) return 0.0f;

        // Map input (-1..1) to normalized index (0..1)
        float normInput = (input + 1.0f) * 0.5f;
        
        // Apply warping
        float warpedIndex = (float) Math.pow(normInput, 1.0 / warp);
        
        int idx = (int)(warpedIndex * (table.length - 1));
        idx = Math.max(0, Math.min(idx, table.length - 1));
        lastOut = table[idx];
        
        return lastOut;
    }

    @Override
    public void coeffs(float[] c) {
        if (c.length == 0) return;
        // Simple fill for now
        for (int i = 0; i < table.length; i++) {
            table[i] = c[i % c.length];
        }
    }
}
