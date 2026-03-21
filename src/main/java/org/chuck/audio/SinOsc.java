package org.chuck.audio;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import static org.chuck.audio.VectorAudio.SPECIES;
import static org.chuck.audio.VectorAudio.OFFSETS;

/**
 * A sine wave oscillator.
 */
public class SinOsc extends Osc {
    public SinOsc(float sampleRate) {
        super(sampleRate);
    }

    @Override
    protected double computeOsc(double phase) {
        return Math.sin(phase * 2.0 * Math.PI);
    }

    @Override
    public void tick(float[] buffer, int offset, int length, long systemTime) {
        // If we have sources (modulation), we must sum them first
        float[] inputSum = new float[length];
        if (getNumSources() > 0) {
            for (ChuckUGen src : sources) {
                float[] temp = new float[length];
                src.tick(temp, 0, length, systemTime);
                for (int j = 0; j < length; j++) inputSum[j] += temp[j];
            }
        }

        float f_freq = (float) freq;
        float f_phase = (float) phase;
        float f_inc = f_freq / sampleRate;
        float twoPi = (float) (2.0 * Math.PI);

        int i = 0;
        int bound = SPECIES.loopBound(length);
        FloatVector vOffsets = FloatVector.fromArray(SPECIES, OFFSETS, 0);
        FloatVector vTwoPi = FloatVector.broadcast(SPECIES, twoPi);
        FloatVector vInc = FloatVector.broadcast(SPECIES, f_inc);

        for (; i < bound; i += SPECIES.length()) {
            // vPhases = (phase + offsets * inc)
            FloatVector vP = vOffsets.mul(vInc).add(f_phase);
            
            // Fractional part only (wrapping to [0, 1])
            var intSpecies = jdk.incubator.vector.VectorSpecies.of(int.class, SPECIES.vectorShape());
            var vIntP = vP.castShape(intSpecies, 0);
            var vFloorP = vIntP.castShape(SPECIES, 0);
            vP = vP.sub(vFloorP);

            // Convert [0, 1] to [-pi, pi] for sine computation
            // x = (vP - 0.5) * 2 * pi
            FloatVector vX = vP.sub(0.5f).mul((float)(2.0 * Math.PI));
            
            // SIMD Sine Approximation: Bhaskara I's approximation or Taylor
            // Let's use a standard high-quality polynomial: 
            // sin(x) approx x - x^3/6 + x^5/120 - x^7/5040
            FloatVector x = vX;
            FloatVector x2 = x.mul(x);
            FloatVector x3 = x2.mul(x);
            FloatVector x5 = x3.mul(x2);
            FloatVector x7 = x5.mul(x2);
            
            FloatVector vSin = x.sub(x3.div(6.0f)).add(x5.div(120.0f)).sub(x7.div(5040.0f));
            
            // If we have modulation, add it here (simplified for now)
            FloatVector vMod = FloatVector.fromArray(SPECIES, inputSum, i);
            vSin = vSin.add(vMod);

            vSin.mul(gain).intoArray(buffer, offset + i);
            
            f_phase = (f_phase + f_inc * SPECIES.length()) % 1.0f;
            if (f_phase < 0) f_phase += 1.0f;
        }

        // Scalar fallback for remainder
        this.phase = f_phase;
        for (; i < length; i++) {
            buffer[offset + i] = tick(systemTime == -1 ? -1 : systemTime + i);
        }
    }
}
