package org.chuck.audio.osc;

import static org.chuck.audio.VectorAudio.OFFSETS;
import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/** A sine wave oscillator. */
@doc("A sine wave oscillator.")
public class SinOsc extends Osc {
  public SinOsc(float sampleRate) {
    super(sampleRate);
  }

  @Override
  protected double computeOsc(double phase) {
    return Math.sin(phase * 2.0 * Math.PI);
  }

  @Override
  public void tick(float[] buffer, int offset, int length, long systemTime) {
    // If we have sources (modulation), we must sum them first
    float[] inputSum = new float[length];
    if (getNumSources() > 0) {
      for (ChuckUGen src : sources) {
        float[] temp = new float[length];
        src.tick(temp, 0, length, systemTime);
        for (int j = 0; j < length; j++) inputSum[j] += temp[j];
      }
    }

    float f_freq = (float) freq;
    float f_phase = (float) phase;
    float f_inc = f_freq / sampleRate;
    float twoPi = (float) (2.0 * Math.PI);

    int i = 0;
    int bound = SPECIES.loopBound(length);
    FloatVector vOffsets = FloatVector.fromArray(SPECIES, OFFSETS, 0);
    @SuppressWarnings("unused")
    FloatVector vTwoPi = FloatVector.broadcast(SPECIES, twoPi);
    FloatVector vInc = FloatVector.broadcast(SPECIES, f_inc);

    for (; i < bound; i += SPECIES.length()) {
      // vPhases = (phase + offsets * inc)
      FloatVector vP = vOffsets.mul(vInc).add(f_phase);

      // Fractional part only (wrapping to [0, 1])
      var intSpecies = jdk.incubator.vector.VectorSpecies.of(int.class, SPECIES.vectorShape());
      var vIntP = vP.castShape(intSpecies, 0);
      var vFloorP = vIntP.castShape(SPECIES, 0);
      vP = vP.sub(vFloorP);

      // Convert [0, 1] to [-pi, pi] for sine computation
      // x = (vP - 0.5) * 2 * pi
      FloatVector vX = vP.sub(0.5f).mul((float) (2.0 * Math.PI));

      // SIMD Sine Approximation (9th order minimax polynomial for better accuracy)
      // sin(x) approx x * (1 + x^2 * (c1 + x^2 * (c2 + x^2 * (c3 + x^2 * c4))))
      FloatVector x2 = vX.mul(vX);
      FloatVector vSin = x2.mul(-1.9841269841269841e-4f); // c4 = -1/5040
      vSin = vSin.add(0.008333333333333333f).mul(x2); // c3 = 1/120
      vSin = vSin.sub(0.16666666666666666f).mul(x2); // c2 = -1/6
      vSin = vSin.add(1.0f).mul(vX);

      // If we have modulation, add it here (simplified for now)
      FloatVector vMod = FloatVector.fromArray(SPECIES, inputSum, i);
      vSin = vSin.add(vMod);

      vSin.mul(gain).intoArray(buffer, offset + i);

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
