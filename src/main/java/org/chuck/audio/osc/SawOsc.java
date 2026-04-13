package org.chuck.audio.osc;

import static org.chuck.audio.VectorAudio.OFFSETS;
import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;

/**
 * Band-limited sawtooth wave oscillator using PolyBLEP correction.
 *
 * <p>The naive sawtooth (2t − 1, reset at t=1→0) has a single discontinuity per cycle. PolyBLEP
 * smooths that jump over one sample period, eliminating the worst aliasing harmonics with
 * negligible CPU cost.
 */
public class SawOsc extends Osc {
  public SawOsc(float sampleRate) {
    super(sampleRate);
    this.freq = 440.0;
  }

  /** Backward-compat alias (ChucK scripts use setFrequency). */
  public void setFrequency(float f) {
    setFreq(f);
  }

  @Override
  protected double computeOsc(double phase) {
    double dt = freq / sampleRate;
    double saw = 2.0 * phase - 1.0; // naive sawtooth [-1, +1)
    saw -= polyBlep(phase, dt); // correct the reset discontinuity at t=0
    return saw;
  }

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

    int i = 0;
    if (getNumSources() == 0) {
      float f_freq = (float) freq;
      float f_phase = (float) phase;
      float f_inc = f_freq / sampleRate;

      int bound = SPECIES.loopBound(length);
      FloatVector vOffsets = FloatVector.fromArray(SPECIES, OFFSETS, 0);
      FloatVector vInc = FloatVector.broadcast(SPECIES, f_inc);
      FloatVector vTwo = FloatVector.broadcast(SPECIES, 2.0f);
      FloatVector vOne = FloatVector.broadcast(SPECIES, 1.0f);

      for (; i < bound; i += SPECIES.length()) {
        // vPhases = (phase + (offsets + 1) * inc)
        FloatVector vPhases = vOffsets.add(1.0f).mul(vInc).add(f_phase);

        // Wrap phases to [0, 1]
        var intSpecies = jdk.incubator.vector.VectorSpecies.of(int.class, SPECIES.vectorShape());
        var vIntP = vPhases.castShape(intSpecies, 0);
        var vFloorP = vIntP.castShape(SPECIES, 0);
        vPhases = vPhases.sub(vFloorP);

        // vSaw = 2 * vPhases - 1 (naive)
        FloatVector vSaw = vPhases.mul(vTwo).sub(vOne);

        // Correct with PolyBLEP
        vSaw = vSaw.sub(vPolyBlep(vPhases, vInc));

        FloatVector vOut = vSaw.mul(gain);
        vOut.intoArray(blockCache, i);
        if (buffer != null) {
          vOut.intoArray(buffer, offset + i);
        }

        f_phase = (f_phase + f_inc * SPECIES.length()) % 1.0f;
      }
      this.phase = f_phase;
    }

    // Scalar fallback for remainder or if we have sources
    for (; i < length; i++) {
      float t = tick(systemTime == -1 ? -1 : systemTime + i);
      blockCache[i] = t;
      if (buffer != null) {
        buffer[offset + i] = t;
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
