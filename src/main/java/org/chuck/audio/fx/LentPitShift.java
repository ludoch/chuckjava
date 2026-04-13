package org.chuck.audio.fx;

import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/**
 * LentPitShift: Formant-preserving pitch shifter using the Lent algorithm. Best for monophonic
 * signals (speech, solo instruments).
 */
@doc("Formant-preserving pitch shifter (Lent algorithm). Best for monophonic signals.")
public class LentPitShift extends ChuckUGen {
  private final float[] inputLine;
  private final float[] outputLine;
  private final float[] window;
  private int inputPtr = 0;
  private int outputPtr = 0;
  private final int tMax;

  private double shift = 1.0;
  private int period = 100;
  private int samplesSinceLastAnalysis = 0;

  public LentPitShift() {
    this(1024); // approx 43Hz min freq at 44.1k
  }

  public LentPitShift(int tMax) {
    this.tMax = tMax;
    this.inputLine = new float[3 * tMax];
    this.outputLine = new float[3 * tMax];
    this.window = new float[2 * tMax];

    // Create Hamming window
    for (int i = 0; i < window.length; i++) {
      window[i] = (float) (0.54 - 0.46 * Math.cos(2.0 * Math.PI * i / (window.length - 1)));
    }
  }

  @doc("Set the pitch shift factor. 1.0 is no shift, 2.0 is an octave up.")
  public double shift(double s) {
    this.shift = Math.max(0.1, s);
    return this.shift;
  }

  public double shift() {
    return shift;
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

    // 1. Sum inputs
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

    // 2. Process
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
    // 1. Store input
    inputLine[inputPtr] = input;

    // 2. Periodic Pitch Analysis (simplified AMDF)
    if (++samplesSinceLastAnalysis >= period) {
      samplesSinceLastAnalysis = 0;
      estimatePeriod();
    }

    // 3. PSOLA-like Overlap Add
    // (This is a simplified version of the Lent loop)
    float out = outputLine[outputPtr];
    outputLine[outputPtr] = 0.0f; // Clear for next cycle

    outputPtr = (outputPtr + 1) % outputLine.length;

    // If we need a new window
    double targetPeriod = period / shift;
    // (Actual Lent logic triggers windows based on pitch marks)
    // For this port, we trigger based on target period interval
    if (outputPtr % (int) Math.max(1, targetPeriod) == 0) {
      int winSize = Math.min(window.length, 2 * period);
      for (int j = 0; j < winSize; j++) {
        int inIdx = (inputPtr - winSize + j + inputLine.length) % inputLine.length;
        int outIdx = (outputPtr + j) % outputLine.length;
        outputLine[outIdx] += inputLine[inIdx] * window[j];
      }
    }

    inputPtr = (inputPtr + 1) % inputLine.length;
    return out;
  }

  private void estimatePeriod() {
    // Basic Average Magnitude Difference Function (AMDF)
    int bestPeriod = period;
    float minDiff = Float.MAX_VALUE;

    for (int tau = 20; tau < tMax; tau++) {
      float diff = 0;
      for (int j = 0; j < tMax; j++) {
        int i1 = (inputPtr - j + inputLine.length) % inputLine.length;
        int i2 = (inputPtr - j - tau + inputLine.length) % inputLine.length;
        diff += Math.abs(inputLine[i1] - inputLine[i2]);
      }
      if (diff < minDiff) {
        minDiff = diff;
        bestPeriod = tau;
      }
    }
    this.period = bestPeriod;
  }
}
