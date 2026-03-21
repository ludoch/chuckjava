package org.chuck.audio;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;
import static org.chuck.audio.VectorAudio.SPECIES;
import static org.chuck.audio.VectorAudio.OFFSETS;

/**
 * Band-limited pulse wave oscillator using PolyBLEP correction.
 *
 * A pulse wave has two discontinuities per cycle: a rising edge at phase=0 and
 * a falling edge at phase=width. Both are corrected independently with PolyBLEP,
 * giving clean output even at high frequencies.
 */
public class PulseOsc extends Osc {
    public PulseOsc(float sampleRate) {
        super(sampleRate);
    }

    @Override
    protected double computeOsc(double phase) {
        double dt = freq / sampleRate;

        // Naive pulse
        double out = (phase < width) ? 1.0 : -1.0;

        // Rising edge at phase = 0
        out += polyBlep(phase, dt);

        // Falling edge at phase = width
        double t2 = phase - width;
        if (t2 < 0.0) t2 += 1.0;
        out -= polyBlep(t2, dt);

        return out;
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
        FloatVector vMinusOne = FloatVector.broadcast(SPECIES, -1.0f);

        for (; i < bound; i += SPECIES.length()) {
            // Raw phase
            FloatVector vPRaw = vOffsets.mul(vInc).add(f_phase);
            
            // p % 1.0 (approximated for positive phases)
            var intSpecies = jdk.incubator.vector.VectorSpecies.of(int.class, SPECIES.vectorShape());
            var vIntP = vPRaw.castShape(intSpecies, 0);
            var vFloorP = vIntP.castShape(SPECIES, 0);
            FloatVector vP = vPRaw.sub(vFloorP);

            VectorMask<Float> mask = vP.lt(vWidth);
            
            // Naive pulse (SIMD)
            FloatVector vOut = vMinusOne.blend(vOne, mask);
            
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
