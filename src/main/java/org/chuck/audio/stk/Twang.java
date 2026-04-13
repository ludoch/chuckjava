package org.chuck.audio.stk;

import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;
import org.chuck.audio.filter.OneZero;
import org.chuck.audio.fx.DelayL;
import org.chuck.core.doc;

/**
 * Twang: Enhanced plucked-string physical model. Based on STK Twang class. Includes a comb filter
 * for pluck position.
 */
@doc("Enhanced plucked-string physical model with pluck position control.")
public class Twang extends ChuckUGen {
  private final DelayL delayLine;
  private final DelayL combDelay;
  private final OneZero loopFilter;
  private float sampleRate;
  private double freq = 440.0;
  private double loopGain = 0.995;
  private double pluckPosition = 0.5;

  public Twang(float sampleRate) {
    this(sampleRate, true);
  }

  public Twang(float sampleRate, boolean autoRegister) {
    super(autoRegister);
    this.sampleRate = sampleRate;
    this.delayLine = new DelayL(2048, sampleRate, false);
    this.combDelay = new DelayL(1024, sampleRate, false);
    this.loopFilter = new OneZero(false);
    this.loopFilter.setZero(0.5f); // Standard lowpass for KS

    updateParameters();
  }

  @doc("Set string frequency in Hz.")
  public double freq(double f) {
    this.freq = Math.max(0.1, f);
    updateParameters();
    return this.freq;
  }

  public double freq() {
    return freq;
  }

  @doc("Set pluck position (0.0 to 1.0). 0.5 is center.")
  public double pluckPos(double p) {
    this.pluckPosition = Math.max(0.0, Math.min(1.0, p));
    updateParameters();
    return this.pluckPosition;
  }

  public double pluckPos() {
    return pluckPosition;
  }

  @doc("Set loop gain (0.0 to 1.0). Controls sustain.")
  public double loopGain(double g) {
    this.loopGain = Math.max(0.0, Math.min(1.0, g));
    return this.loopGain;
  }

  public double loopGain() {
    return loopGain;
  }

  private void updateParameters() {
    double totalDelay = sampleRate / freq;
    // Compensation for loop filter phase delay
    delayLine.delay(totalDelay - 0.5);

    // Pluck position comb filter delay
    combDelay.delay(0.5 * pluckPosition * totalDelay);
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

    // 1. Sum inputs (excitation)
    float[] inputSum = new float[length];
    java.util.List<ChuckUGen> srcs = getSources();
    if (!srcs.isEmpty()) {
      for (ChuckUGen src : srcs) {
        float[] temp = new float[length];
        src.tick(temp, 0, length, systemTime);
        int i = 0;
        int bound = SPECIES.loopBound(length);
        for (; i < bound; i += SPECIES.length()) {
          FloatVector v1 = FloatVector.fromArray(SPECIES, inputSum, i);
          FloatVector v2 = FloatVector.fromArray(SPECIES, temp, i);
          v1.add(v2).intoArray(inputSum, i);
        }
        for (; i < length; i++) inputSum[i] += temp[i];
      }
    }

    // 2. String processing (scalar recursive)
    for (int i = 0; i < length; i++) {
      blockCache[i] = compute(inputSum[i], systemTime == -1 ? -1 : systemTime + i) * gain;
      if (buffer != null) buffer[offset + i] = blockCache[i];
    }

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = (systemTime == -1) ? -1 : systemTime + length - 1;
    if (length > 0) lastOut = blockCache[length - 1];
  }

  @Override
  protected float compute(float input, long systemTime) {
    // Waveguide loop
    float feedback = loopFilter.tick(delayLine.last(), systemTime);
    float val = input + (float) (feedback * (loopGain + freq * 0.000005));
    delayLine.tick(val, systemTime);

    // Pluck position comb filter: y = x[n] - x[n - pluckDelay]
    combDelay.tick(val, systemTime);
    return val - combDelay.last();
  }
}
