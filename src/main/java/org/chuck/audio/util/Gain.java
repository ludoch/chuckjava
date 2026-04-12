// Note: Requires --add-modules jdk.incubator.vector
// In JDK 25, this is still the standard location for Vector API.
package org.chuck.audio.util;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/**
 * A Gain UGen that can apply a multiplier to its input. Demonstrates the JDK Vector API for block
 * processing.
 */
@doc("Gain control UGen.")
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

  /** SIMD Optimized block processing using the JDK 25 Vector API. */
  @Override
  public void tick(float[] buffer, int offset, int length, long systemTime) {
    if (systemTime != -1
        && systemTime == lastTickTime
        && blockCache != null
        && blockCache.length >= length) {
      if (buffer != null) {
        System.arraycopy(blockCache, 0, buffer, offset, length);
      }
      return;
    }

    if (blockCache == null || blockCache.length < length) {
      blockCache = new float[length];
    }

    int i = 0;
    if (buffer != null) {
      int upperBound = SPECIES.loopBound(length);

      // Multiplier vector
      FloatVector vGain = FloatVector.broadcast(SPECIES, this.gain);

      // Vectorized loop
      for (; i < upperBound; i += SPECIES.length()) {
        // Load block from buffer
        var vIn = FloatVector.fromArray(SPECIES, buffer, offset + i);
        // Apply gain
        var vOut = vIn.mul(vGain);
        // Store back
        vOut.intoArray(buffer, offset + i);
      }

      // Tail loop for remaining elements
      for (; i < length; i++) {
        buffer[offset + i] *= this.gain;
      }

      // Update lastOut for potential scalar callers
      if (length > 0) {
        lastOut = buffer[offset + length - 1];
      }
    }

    if (buffer != null) {
      System.arraycopy(buffer, offset, blockCache, 0, length);
    }
    lastTickTime = systemTime;
  }
}
