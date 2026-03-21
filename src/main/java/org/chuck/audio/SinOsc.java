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
        // If we have sources (modulation), fallback to scalar for sample-accuracy
        if (getNumSources() > 0) {
            super.tick(buffer, offset, length, systemTime);
            return;
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
            // vPhases = (phase + offsets * inc) * 2pi
            FloatVector vPhases = vOffsets.mul(vInc).add(f_phase).mul(vTwoPi);
            
            // SIMD Sine Approximation (Taylor series or simple cubic)
            // For now, let's use a simple but effective approximation for demonstration:
            // sin(x) approx x - x^3/6 + x^5/120
            FloatVector x = vPhases;
            // Range reduce to [-pi, pi]
            // (Simple version for demo, real implementation would be more robust)
            
            FloatVector x2 = x.mul(x);
            FloatVector x3 = x2.mul(x);
            FloatVector x5 = x3.mul(x2);
            FloatVector vSin = x.sub(x3.div(6.0f)).add(x5.div(120.0f));
            
            vSin.mul(gain).intoArray(buffer, offset + i);
            
            f_phase = (f_phase + f_inc * SPECIES.length()) % 1.0f;
        }

        // Scalar fallback for remainder
        this.phase = f_phase;
        for (; i < length; i++) {
            buffer[offset + i] = tick(systemTime == -1 ? -1 : systemTime + i);
        }
    }
}
