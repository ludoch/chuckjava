package org.chuck.audio;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;
import static org.chuck.audio.VectorAudio.SPECIES;
import static org.chuck.audio.VectorAudio.OFFSETS;

/**
 * Triangle wave oscillator.
 *
 * Triangle is C0-continuous (no amplitude discontinuities) and its harmonics
 * roll off as 1/n², so aliasing is inherently low. Unlike SawOsc and PulseOsc,
 * no PolyBLEP correction is needed or applied here.
 */
public class TriOsc extends Osc {
    public TriOsc(float sampleRate) {
        super(sampleRate);
    }

    @Override
    protected double computeOsc(double phase) {
        // Shift phase by 0.25 to match ChucK's TriOsc definition
        double p = phase + 0.25;
        if (p > 1.0) p -= 1.0;

        if (p < width) {
            return (width == 0.0) ? 1.0 : -1.0 + 2.0 * p / width;
        } else {
            return (width == 1.0) ? 0.0 : 1.0 - 2.0 * (p - width) / (1.0 - width);
        }
    }

    @Override
    public void tick(float[] buffer, int offset, int length, long systemTime) {
        if (getNumSources() > 0) {
            super.tick(buffer, offset, length, systemTime);
            return;
        }

        float f_freq = (float) freq;
        float f_phase = (float) phase;
        float f_inc = f_freq / sampleRate;
        float f_width = (float) width;

        int i = 0;
        int bound = SPECIES.loopBound(length);
        FloatVector vOffsets = FloatVector.fromArray(SPECIES, OFFSETS, 0);
        FloatVector vInc = FloatVector.broadcast(SPECIES, f_inc);
        FloatVector vWidth = FloatVector.broadcast(SPECIES, f_width);
        FloatVector vOne = FloatVector.broadcast(SPECIES, 1.0f);
        FloatVector vTwo = FloatVector.broadcast(SPECIES, 2.0f);
        FloatVector vZero = FloatVector.zero(SPECIES);
        
        float widthFactor1 = (f_width == 0.0f) ? 0.0f : 2.0f / f_width;
        float widthFactor2 = (f_width == 1.0f) ? 0.0f : 2.0f / (1.0f - f_width);
        FloatVector vWidthFactor1 = FloatVector.broadcast(SPECIES, widthFactor1);
        FloatVector vWidthFactor2 = FloatVector.broadcast(SPECIES, widthFactor2);

        for (; i < bound; i += SPECIES.length()) {
            // Raw phase
            FloatVector vPRaw = vOffsets.mul(vInc).add(f_phase).add(0.25f);
            
            // p % 1.0 (approximated for positive phases)
            // For proper modulo, we'd subtract the floor. 
            // In Java Vector API, there isn't a direct floor for floats in all architectures without casts,
            // but we can do a quick wrap if we assume phases are generally positive and we keep them in [0,1].
            // Let's cast to int and back to subtract.
            var intSpecies = jdk.incubator.vector.VectorSpecies.of(int.class, SPECIES.vectorShape());
            var vIntP = vPRaw.castShape(intSpecies, 0);
            var vFloorP = vIntP.castShape(SPECIES, 0);
            FloatVector vP = vPRaw.sub(vFloorP);

            VectorMask<Float> mask = vP.lt(vWidth);
            
            // True branch: -1.0 + 2.0 * p / width  ->  p * widthFactor1 - 1.0
            FloatVector vTrue = vP.mul(vWidthFactor1).sub(vOne);
            // Edge case width == 0
            if (f_width == 0.0f) vTrue = vOne;

            // False branch: 1.0 - 2.0 * (p - width) / (1.0 - width) -> 1.0 - (p - width) * widthFactor2
            FloatVector vFalse = vOne.sub( vP.sub(vWidth).mul(vWidthFactor2) );
            if (f_width == 1.0f) vFalse = vZero;

            FloatVector vOut = vFalse.blend(vTrue, mask);
            
            vOut.mul(gain).intoArray(buffer, offset + i);
            
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
