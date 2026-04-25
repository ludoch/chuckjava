package org.chuck.audio.osc;

import static org.chuck.audio.VectorAudio.OFFSETS;
import static org.chuck.audio.VectorAudio.SPECIES;

import java.util.List;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import org.chuck.audio.ChuckUGen;

/** Pulse wave oscillator. Matches native ChucK (non-band-limited, samples BEFORE increment). */
public class PulseOsc extends Osc {
  public PulseOsc() {
    super();
  }

  public PulseOsc(float sampleRate) {
    super(sampleRate);
  }

  @Override
  protected double computeOsc(double phase) {
    return (phase < width) ? 1.0 : -1.0;
  }

  @Override
  public void tick(float[] buffer, int offset, int length, long systemTime) {
    tick(buffer, offset, length, systemTime, null);
  }

  @Override
  public void tick(float[] buffer, int offset, int length, long systemTime, float[] manualInput) {
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
    List<ChuckUGen> srcs = getSources();
    if (srcs.isEmpty() && manualInput == null) {
      float f_phase = (float) phase;
      float f_inc = (float) num;
      float f_width = (float) width;

      int bound = SPECIES.loopBound(length);
      FloatVector vOffsets = FloatVector.fromArray(SPECIES, OFFSETS, 0);
      FloatVector vInc = FloatVector.broadcast(SPECIES, f_inc);
      FloatVector vWidth = FloatVector.broadcast(SPECIES, f_width);
      FloatVector vOne = FloatVector.broadcast(SPECIES, 1.0f);
      FloatVector vMinusOne = FloatVector.broadcast(SPECIES, -1.0f);

      for (; i < bound; i += SPECIES.length()) {
        // Raw phase (No +1 offset to match ChucK's sample-before-increment)
        FloatVector vPhases = vOffsets.mul(vInc).add(f_phase);

        // Wrap phases to [0, 1]
        var intSpecies = jdk.incubator.vector.VectorSpecies.of(int.class, SPECIES.vectorShape());
        var vIntP = vPhases.castShape(intSpecies, 0);
        var vFloorP = vIntP.castShape(SPECIES, 0);
        vPhases = vPhases.sub(vFloorP);

        VectorMask<Float> mask = vPhases.compare(jdk.incubator.vector.VectorOperators.LT, vWidth);

        // Naive pulse (SIMD)
        FloatVector vOut = vMinusOne.blend(vOne, mask);

        FloatVector vGainOut = vOut.mul(gain);
        vGainOut.intoArray(blockCache, i);
        if (buffer != null) {
          vGainOut.intoArray(buffer, offset + i);
        }

        f_phase = (f_phase + f_inc * SPECIES.length()) % 1.0f;
        if (f_phase < 0) f_phase += 1.0f;
      }
      this.phase = f_phase;
    }

    // Scalar fallback for remainder or if we have sources
    for (; i < length; i++) {
      float in = (manualInput != null) ? manualInput[i] : 0.0f;
      float t = tick(in, systemTime == -1 ? -1 : systemTime + i);
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
