package org.chuck.audio;

/**
 * Gen9: Sum of sinusoids with arbitrary frequency, amplitude, and phase ratios.
 * ChucK: [1.0, 1.0, 0.0,  2.0, 0.5, 0.0] => gen9.coeffs; // [freq, amp, phase, ...]
 */
public class Gen9 extends GenX {
    public Gen9(float sampleRate) { super(sampleRate); }

    @Override
    public void coeffs(float[] c) {
        if (c.length < 3) return;
        
        for (int i = 0; i < table.length; i++) {
            double val = 0;
            double normPhase = (double) i / table.length;
            for (int h = 0; h < c.length; h += 3) {
                if (h + 2 >= c.length) break;
                double freqRatio = c[h];
                double amp = c[h+1];
                double phaseOffset = c[h+2];
                val += amp * Math.sin(2.0 * Math.PI * (normPhase * freqRatio + phaseOffset));
            }
            table[i] = (float) val;
        }
    }
}
