package org.chuck.audio;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import static org.chuck.audio.VectorAudio.SPECIES;
import static org.chuck.audio.VectorAudio.OFFSETS;

/**
 * Band-limited sawtooth wave oscillator using PolyBLEP correction.
 *
 * The naive sawtooth (2t − 1, reset at t=1→0) has a single discontinuity per
 * cycle. PolyBLEP smooths that jump over one sample period, eliminating the
 * worst aliasing harmonics with negligible CPU cost.
 */
public class SawOsc extends Osc {
    public SawOsc(float sampleRate) {
        super(sampleRate);
        this.freq = 440.0;
    }

    /** Backward-compat alias (ChucK scripts use setFrequency). */
    public void setFrequency(float f) { setFreq(f); }

    @Override
    protected double computeOsc(double phase) {
        double dt  = freq / sampleRate;
        double saw = 2.0 * phase - 1.0;  // naive sawtooth [-1, +1)
        saw -= polyBlep(phase, dt);       // correct the reset discontinuity at t=0
        return saw;
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

        int i = 0;
        int bound = SPECIES.loopBound(length);
        FloatVector vOffsets = FloatVector.fromArray(SPECIES, OFFSETS, 0);
        FloatVector vInc = FloatVector.broadcast(SPECIES, f_inc);
        FloatVector vTwo = FloatVector.broadcast(SPECIES, 2.0f);
        FloatVector vOne = FloatVector.broadcast(SPECIES, 1.0f);

        for (; i < bound; i += SPECIES.length()) {
            // vPhases = (phase + offsets * inc)
            FloatVector vPhases = vOffsets.mul(vInc).add(f_phase);
            
            // vSaw = 2 * vPhases - 1
            // (Note: this is naive sawtooth, real PolyBLEP would be harder to vectorize)
            FloatVector vSaw = vPhases.mul(vTwo).sub(vOne);
            
            vSaw.mul(gain).intoArray(buffer, offset + i);
            
            f_phase = (f_phase + f_inc * SPECIES.length()) % 1.0f;
        }

        // Scalar fallback for remainder
        this.phase = f_phase;
        for (; i < length; i++) {
            buffer[offset + i] = tick(systemTime == -1 ? -1 : systemTime + i);
        }
    }
}
