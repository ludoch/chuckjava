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
        && systemTime == blockStartTime
        && blockCache != null
        && blockLength >= length) {
      if (buffer != null) {
        System.arraycopy(blockCache, 0, buffer, offset, length);
      }
      return;
    }

    if (blockCache == null || blockCache.length < length) {
      blockCache = new float[length];
    }
    java.util.Arrays.fill(blockCache, 0, length, 0.0f);

    // Vectorized summing from all sources
    for (org.chuck.audio.ChuckUGen src : sources) {
      float[] temp = new float[length];
      src.tick(temp, 0, length, systemTime);

      // SIMD Addition: blockCache += temp
      int i = 0;
      int bound = SPECIES.loopBound(length);
      for (; i < bound; i += SPECIES.length()) {
        FloatVector vSum = FloatVector.fromArray(SPECIES, blockCache, i);
        FloatVector vSrc = FloatVector.fromArray(SPECIES, temp, i);
        vSum.add(vSrc).intoArray(blockCache, i);
      }
      // Fallback
      for (; i < length; i++) {
        blockCache[i] += temp[i];
      }
    }

    int i = 0;
    int upperBound = SPECIES.loopBound(length);

    // Multiplier vector
    FloatVector vGain = FloatVector.broadcast(SPECIES, this.gain);

    // Vectorized multiplication loop
    for (; i < upperBound; i += SPECIES.length()) {
      var vIn = FloatVector.fromArray(SPECIES, blockCache, i);
      var vOut = vIn.mul(vGain);
      vOut.intoArray(blockCache, i);
      if (buffer != null) {
        vOut.intoArray(buffer, offset + i);
      }
    }

    // Tail loop for remaining elements
    for (; i < length; i++) {
      float out = blockCache[i] * this.gain;
      blockCache[i] = out;
      if (buffer != null) {
        buffer[offset + i] = out;
      }
    }

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = systemTime + length - 1;
    if (length > 0) {
      lastOut = blockCache[length - 1];
    }
  }
}
