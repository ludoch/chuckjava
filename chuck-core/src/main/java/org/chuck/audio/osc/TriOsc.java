package org.chuck.audio.osc;

import static org.chuck.audio.VectorAudio.OFFSETS;
import static org.chuck.audio.VectorAudio.SPECIES;

import java.util.List;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import org.chuck.audio.ChuckUGen;

/** Triangle wave oscillator. Matches native ChucK (non-band-limited, samples BEFORE increment). */
public class TriOsc extends Osc {
  public TriOsc() {
    super();
  }

  public TriOsc(float sampleRate) {
    super(sampleRate);
  }

  @Override
  protected double computeOsc(double phase) {
    // Shift phase by 0.25 to match ChucK's TriOsc definition
    double p = phase + 0.25;
    // Native ChucK uses > 1.0, not >= 1.0!
    // "t_CKFLOAT phase = d->phase + .25; if( phase > 1.0 ) phase -= 1.0;"
    if (p > 1.0) p -= 1.0;

    if (p < width) {
      // (d->width == 0.0) ? 1.0 : -1.0 + 2.0 * phase / d->width
      return (width == 0.0) ? 1.0 : -1.0 + 2.0 * p / width;
    } else {
      // (d->width == 1.0) ? 0 : 1.0 - 2.0 * (phase - d->width) / (1.0 - d->width)
      return (width == 1.0) ? 0.0 : 1.0 - 2.0 * (p - width) / (1.0 - width);
    }
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
      FloatVector vZero = FloatVector.zero(SPECIES);

      float widthFactor1 = (f_width == 0.0f) ? 0.0f : 2.0f / f_width;
      float widthFactor2 = (f_width == 1.0f) ? 0.0f : 2.0f / (1.0f - f_width);
      FloatVector vWidthFactor1 = FloatVector.broadcast(SPECIES, widthFactor1);
      FloatVector vWidthFactor2 = FloatVector.broadcast(SPECIES, widthFactor2);

      for (; i < bound; i += SPECIES.length()) {
        // Raw phase
        FloatVector vPRaw = vOffsets.mul(vInc).add(f_phase).add(0.25f);

        // p % 1.0 (approximated for positive phases)
        var intSpecies = jdk.incubator.vector.VectorSpecies.of(int.class, SPECIES.vectorShape());
        var vIntP = vPRaw.castShape(intSpecies, 0);
        var vFloorP = vIntP.castShape(SPECIES, 0);
        FloatVector vP = vPRaw.sub(vFloorP);

        VectorMask<Float> mask = vP.compare(jdk.incubator.vector.VectorOperators.LT, vWidth);

        // True branch: -1.0 + 2.0 * p / width
        FloatVector vTrue = vP.mul(vWidthFactor1).sub(vOne);
        if (f_width == 0.0f) vTrue = vOne;

        // False branch: 1.0 - 2.0 * (p - width) / (1.0 - width)
        FloatVector vFalse = vOne.sub(vP.sub(vWidth).mul(vWidthFactor2));
        if (f_width == 1.0f) vFalse = vZero;

        FloatVector vOut = vFalse.blend(vTrue, mask).mul(gain);
        vOut.intoArray(blockCache, i);

        if (buffer != null) {
          vOut.intoArray(buffer, offset + i);
        }

        f_phase = (f_phase + f_inc * SPECIES.length()) % 1.0f;
        if (f_phase < 0) f_phase += 1.0f;
      }
      this.phase = f_phase;
    }

    // Scalar fallback
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
    if (length > 0) {
      lastOut = blockCache[length - 1];
      lastTickTime = systemTime + length - 1;
    }
  }

  @Override
  public void tick(float[] buffer, int offset, int length, long systemTime) {
    tick(buffer, offset, length, systemTime, null);
  }
}
