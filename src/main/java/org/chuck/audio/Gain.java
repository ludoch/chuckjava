package org.chuck.audio;

// Note: Requires --add-modules jdk.incubator.vector
// In JDK 25, this is still the standard location for Vector API.
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * A Gain UGen that can apply a multiplier to its input.
 * Demonstrates the JDK Vector API for block processing.
 */
public class Gain extends ChuckUGen {
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    @Override
    protected float compute(float input, long systemTime) {
        // Gain just passes the input through (it's multiplied by this.gain in the base class)
        return input;
    }

    public float db(float db) {
        this.gain = (float) Math.pow(10.0, db / 20.0);
        return db;
    }

    public float db() {
        return (float) (20.0 * Math.log10(this.gain));
    }

    public void setDb(float db) {
        db(db);
    }

    /**
     * SIMD Optimized block processing using the JDK 25 Vector API.
     */
    @Override
    public void tick(float[] buffer) {
        int i = 0;
        int upperBound = SPECIES.loopBound(buffer.length);
        
        // Multiplier vector
        FloatVector vGain = FloatVector.broadcast(SPECIES, this.gain);

        // Vectorized loop
        for (; i < upperBound; i += SPECIES.length()) {
            // Load block from buffer (which might have been filled by previous UGens)
            var vIn = FloatVector.fromArray(SPECIES, buffer, i);
            // Apply gain
            var vOut = vIn.mul(vGain);
            // Store back
            vOut.intoArray(buffer, i);
        }

        // Tail loop for remaining elements
        for (; i < buffer.length; i++) {
            buffer[i] *= this.gain;
        }
    }
}
