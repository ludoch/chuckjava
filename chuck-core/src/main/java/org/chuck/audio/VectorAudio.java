package org.chuck.audio;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

/** Common configuration for Vectorized Audio operations. */
public class VectorAudio {
  /** Optimal vector size for the current hardware (e.g. 256-bit or 512-bit). */
  public static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

  /** Precomputed offsets [0, 1, 2, ..., SPECIES.length()-1] */
  public static final float[] OFFSETS;

  static {
    OFFSETS = new float[SPECIES.length()];
    for (int i = 0; i < OFFSETS.length; i++) OFFSETS[i] = i;
  }
}
