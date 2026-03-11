package org.chuck.audio;

/**
 * Gen5: Exponential interpolation between points.
 * ChucK: [val1, dur1, val2, dur2, val3] => gen5.coeffs;
 */
public class Gen5 extends GenX {
    public Gen5(float sampleRate) { super(sampleRate); }

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
            
            // Handle 0 values for log/exp interpolation
            if (startVal == 0) startVal = 0.0001f;
            if (endVal == 0) endVal = 0.0001f;

            double factor = Math.pow(endVal / startVal, 1.0 / dur);
            double val = startVal;
            
            for (int j = 0; j < dur; j++) {
                table[currentIdx++] = (float) val;
                val *= factor;
            }
        }
        if (currentIdx < table.length) table[currentIdx] = c[c.length - 1];
    }
}
