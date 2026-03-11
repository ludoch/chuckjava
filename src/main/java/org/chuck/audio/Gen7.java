package org.chuck.audio;

/**
 * Gen7: Linear interpolation between points.
 * ChucK: [val1, dur1, val2, dur2, val3] => gen7.coeffs;
 */
public class Gen7 extends GenX {
    public Gen7(float sampleRate) { super(sampleRate); }

    public void coeffs(float[] c) {
        if (c.length < 3) return;
        int numSegments = (c.length - 1) / 2;
        int totalDur = 0;
        for (int i = 0; i < numSegments; i++) totalDur += (int) c[i*2 + 1];
        
        if (totalDur == 0) return;
        if (table.length != totalDur) table = new float[totalDur];

        int currentIdx = 0;
        for (int i = 0; i < numSegments; i++) {
            float startVal = c[i*2];
            int dur = (int) c[i*2 + 1];
            float endVal = c[i*2 + 2];
            
            for (int j = 0; j < dur; j++) {
                table[currentIdx++] = startVal + (endVal - startVal) * ((float)j / dur);
            }
        }
        // Fill last sample
        if (currentIdx < table.length) table[currentIdx] = c[c.length - 1];
    }
}
