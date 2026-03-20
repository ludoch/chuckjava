package org.chuck.audio;

/**
 * Gen10: Sum of sinusoids.
 * ChucK: [1.0, 0.5, 0.25] => gen10.coeffs; (weights for harmonics 1, 2, 3...)
 */
public class Gen10 extends GenX {
    public Gen10(float sampleRate) { super(sampleRate); }

    @Override
    public void coeffs(float[] c) {
        if (c.length == 0) return;
        
        for (int i = 0; i < table.length; i++) {
            double val = 0;
            double phase = 2.0 * Math.PI * i / table.length;
            for (int h = 0; h < c.length; h++) {
                val += c[h] * Math.sin(phase * (h + 1));
            }
            table[i] = (float) val;
        }
    }
}
