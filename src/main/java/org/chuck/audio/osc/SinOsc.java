package org.chuck.audio.osc;

import static org.chuck.audio.VectorAudio.OFFSETS;
import static org.chuck.audio.VectorAudio.SPECIES;

import java.util.List;
import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/** A sine wave oscillator using high-accuracy SIMD polynomial approximation. */
@doc("A sine wave oscillator.")
public class SinOsc extends Osc {
  public SinOsc(float sampleRate) {
    super(sampleRate);
  }

  @Override
  protected double computeOsc(double phase) {
    // Use the same polynomial as SIMD for bit-exact parity
    // Wrap phase to [-0.5, 0.5] for better polynomial accuracy
    float p = (float) (phase > 0.5 ? phase - 1.0 : phase);
    float x = p * (float) (2.0 * Math.PI);
    float x2 = x * x;

    // 9th order minimax polynomial for sin(x)
    float s = x2 * -1.9841269841269841e-4f; // c4 = -1/5040
    s = (s + 0.008333333333333333f) * x2; // c3 = 1/120
    s = (s - 0.16666666666666666f) * x2; // c2 = -1/6
    s = (s + 1.0f) * x;
    return s;
  }

  @Override
  public void tick(float[] buffer, int offset, int length, long systemTime) {
    if (systemTime != -1
        && systemTime == blockStartTime
        && blockCache != null
        && blockLength >= length) {
      if (buffer != null) System.arraycopy(blockCache, 0, buffer, offset, length);
      return;
    }
    if (blockCache == null || blockCache.length < length) blockCache = new float[length];

    // If we have sources (modulation), we must sum them first
    float[] inputSum = new float[length];
    List<ChuckUGen> srcs = getSources();
    if (!srcs.isEmpty()) {
      for (ChuckUGen src : srcs) {
        float[] temp = new float[length];
        src.tick(temp, 0, length, systemTime);
        for (int j = 0; j < length; j++) inputSum[j] += temp[j];
      }
    }

    float f_freq = (float) freq;
    float f_phase = (float) phase;
    float f_inc = f_freq / sampleRate;

    int i = 0;
    int bound = SPECIES.loopBound(length);
    FloatVector vOffsets = FloatVector.fromArray(SPECIES, OFFSETS, 0);
    FloatVector vInc = FloatVector.broadcast(SPECIES, f_inc);
    FloatVector vOne = FloatVector.broadcast(SPECIES, 1.0f);
    FloatVector vHalf = FloatVector.broadcast(SPECIES, 0.5f);
    FloatVector vTwoPi = FloatVector.broadcast(SPECIES, (float) (2.0 * Math.PI));

    for (; i < bound; i += SPECIES.length()) {
      // vPhases = (phase + (offsets + 1) * inc)
      // ChucK increments phase BEFORE computing the sample
      FloatVector vP = vOffsets.add(1.0f).mul(vInc).add(f_phase);

      // Wrap phases to [0, 1]
      var intSpecies = jdk.incubator.vector.VectorSpecies.of(int.class, SPECIES.vectorShape());
      var vIntP = vP.castShape(intSpecies, 0);
      var vFloorP = vIntP.castShape(SPECIES, 0);
      vP = vP.sub(vFloorP);

      // Wrap [0, 1] to [-0.5, 0.5] for better polynomial accuracy
      var vMask = vP.compare(jdk.incubator.vector.VectorOperators.GT, vHalf);
      FloatVector vWrappedP = vP.sub(vOne.blend(FloatVector.zero(SPECIES), vMask.not()));

      FloatVector vX = vWrappedP.mul(vTwoPi);

      // SIMD Sine Approximation (9th order)
      FloatVector x2 = vX.mul(vX);
      FloatVector vSin = x2.mul(-1.9841269841269841e-4f);
      vSin = vSin.add(0.008333333333333333f).mul(x2);
      vSin = vSin.sub(0.16666666666666666f).mul(x2);
      vSin = vSin.add(1.0f).mul(vX);

      // Add modulation
      FloatVector vMod = FloatVector.fromArray(SPECIES, inputSum, i);
      vSin = vSin.add(vMod);

      FloatVector vOut = vSin.mul(gain);
      vOut.intoArray(blockCache, i);
      if (buffer != null) {
        vOut.intoArray(buffer, offset + i);
      }

      f_phase = (f_phase + f_inc * SPECIES.length()) % 1.0f;
      if (f_phase < 0) f_phase += 1.0f;
    }

    // Scalar fallback for remainder
    this.phase = f_phase;
    for (; i < length; i++) {
      float out = tick(systemTime == -1 ? -1 : systemTime + i);
      blockCache[i] = out;
      if (buffer != null) buffer[offset + i] = out;
    }

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = systemTime + length - 1;
    if (length > 0) {
      lastOut = blockCache[length - 1];
    }
  }
}
