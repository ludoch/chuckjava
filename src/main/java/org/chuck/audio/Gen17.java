package org.chuck.audio;

/**
 * Gen17: Chebyshev polynomials of the first kind.
 * Used for wavefolding/distortion.
 * ChucK: [1.0, 0.5, 0.25] => gen17.coeffs; // coefficients for T1, T2, T3...
 */
public class Gen17 extends GenX {
    public Gen17(float sampleRate) { super(sampleRate); }

    @Override
    public void coeffs(float[] c) {
        if (c.length == 0) return;
        
        for (int i = 0; i < table.length; i++) {
            // Input x from -1.0 to 1.0
            double x = 2.0 * i / (table.length - 1) - 1.0;
            double val = 0;
            
            // T0 = 1, T1 = x
            double tMinus2 = 1; // T0
            double tMinus1 = x; // T1
            
            // ChucK Gen17 starts with T1 (some implementations start with T0)
            if (c.length > 0) val += c[0] * tMinus1;
            
            for (int n = 2; n <= c.length; n++) {
                double t = 2.0 * x * tMinus1 - tMinus2;
                val += c[n-1] * t;
                tMinus2 = tMinus1;
                tMinus1 = t;
            }
            table[i] = (float) val;
        }
    }
}
