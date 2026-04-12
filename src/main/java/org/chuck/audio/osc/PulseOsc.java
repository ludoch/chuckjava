package org.chuck.audio.osc;

import static org.chuck.audio.VectorAudio.OFFSETS;
import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;

/**
 * Band-limited pulse wave oscillator using PolyBLEP correction.
 *
 * <p>A pulse wave has two discontinuities per cycle: a rising edge at phase=0 and a falling edge at
 * phase=width. Both are corrected independently with PolyBLEP, giving clean output even at high
 * frequencies.
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
    if (systemTime != -1
        && systemTime == lastTickTime
        && blockCache != null
        && blockCache.length >= length) {
      if (buffer != null) {
        System.arraycopy(blockCache, 0, buffer, offset, length);
      }
      return;
    }

    if (getNumSources() > 0) {
      super.tick(buffer, offset, length, systemTime);
      return;
    }

    if (blockCache == null || blockCache.length < length) {
      blockCache = new float[length];
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

      // p % 1.0
      var intSpecies = jdk.incubator.vector.VectorSpecies.of(int.class, SPECIES.vectorShape());
      var vIntP = vPRaw.castShape(intSpecies, 0);
      var vFloorP = vIntP.castShape(SPECIES, 0);
      FloatVector vP = vPRaw.sub(vFloorP);

      VectorMask<Float> mask = vP.compare(jdk.incubator.vector.VectorOperators.LT, vWidth);

      // Naive pulse (SIMD)
      FloatVector vOut = vMinusOne.blend(vOne, mask);

      // Rising edge at phase 0
      vOut = vOut.add(vPolyBlep(vP, vInc));

      // Falling edge at phase width
      FloatVector vP2 = vP.sub(vWidth);
      // Wrap vP2 to [0, 1]
      VectorMask<Float> maskNeg = vP2.compare(jdk.incubator.vector.VectorOperators.LT, 0.0f);
      vP2 = vP2.add(vOne.blend(FloatVector.zero(SPECIES), maskNeg));

      vOut = vOut.sub(vPolyBlep(vP2, vInc));

      FloatVector vGainOut = vOut.mul(gain);
      vGainOut.intoArray(blockCache, i);
      if (buffer != null) {
        vGainOut.intoArray(buffer, offset + i);
      }

      f_phase = (f_phase + f_inc * SPECIES.length()) % 1.0f;
      if (f_phase < 0) f_phase += 1.0f;
    }

    // Scalar fallback for remainder
    this.phase = f_phase;
    for (; i < length; i++) {
      float t = tick(systemTime == -1 ? -1 : systemTime + i);
      blockCache[i] = t;
      if (buffer != null) {
        buffer[offset + i] = t;
      }
    }

    lastTickTime = systemTime;
    if (length > 0) {
      lastOut = blockCache[length - 1];
    }
  }
}
