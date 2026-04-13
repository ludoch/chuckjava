package org.chuck.audio.util;

import static org.chuck.audio.VectorAudio.SPECIES;

import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;

/** A mono-to-stereo unit generator for stereo panning. */
public class Pan2 extends StereoUGen {
  public final Gain left = new Gain();
  public final Gain right = new Gain();
  private float pan = 0.0f; // -1 (left) to 1 (right)
  private int panType = 1; // 0: linear, 1: constant power

  // Precalculated gains for block processing
  private float gL = 0.707f;
  private float gR = 0.707f;

  private float[][] multiBlockCache;

  public Pan2() {
    this.numInputs = 2;
    this.numOutputs = 2;
    this.inputChannels = new ChuckUGen[] {new Gain(), new Gain()};
    this.outputChannels = new ChuckUGen[] {left, right};

    // Internal routing for connection tracking
    inputChannels[0].chuckTo(left);
    inputChannels[1].chuckTo(right);

    // Also connect to main processing for ticking
    inputChannels[0].chuckTo(this);
    inputChannels[1].chuckTo(this);

    updateGains();
  }

  private void updateGains() {
    if (panType == 1) { // Constant power
      double angle = (pan + 1.0) * (Math.PI / 4.0);
      gL = (float) Math.cos(angle);
      gR = (float) Math.sin(angle);
    } else { // Linear
      gL = (1.0f - pan) * 0.5f;
      gR = (pan + 1.0f) * 0.5f;
    }
  }

  public void setPan(float pan) {
    this.pan = pan;
    updateGains();
  }

  public float getPan() {
    return pan;
  }

  /** ChucK-style: p.pan(0.5) */
  public float pan(float p) {
    this.pan = p;
    updateGains();
    return p;
  }

  /** ChucK-style: p.pan() */
  public float pan() {
    return pan;
  }

  public void setPanType(int type) {
    this.panType = type;
    updateGains();
  }

  /** ChucK-style: p.panType(1) */
  public int panType(int t) {
    this.panType = t;
    updateGains();
    return t;
  }

  @Override
  public float getChannelLastOut(int i, long systemTime) {
    if (systemTime != -1
        && blockLength > 0
        && systemTime >= blockStartTime
        && systemTime < blockStartTime + blockLength) {
      int idx = (int) (systemTime - blockStartTime);
      if (i >= 0 && i < 2 && multiBlockCache != null) {
        return multiBlockCache[i][idx];
      }
    }
    return getChannelLastOut(i);
  }

  @Override
  public void tick(float[] buffer, int offset, int length, long systemTime) {
    if (systemTime != -1
        && systemTime == blockStartTime
        && blockLength >= length
        && multiBlockCache != null) {
      if (buffer != null) {
        System.arraycopy(multiBlockCache[0], 0, buffer, offset, length);
      }
      return;
    }

    if (multiBlockCache == null || multiBlockCache[0].length < length) {
      multiBlockCache = new float[2][length];
    }

    // Pull audio from sources
    float[] inBuf = new float[length];
    if (getNumSources() > 0) {
      sources.get(0).tick(inBuf, 0, length, systemTime);
    }

    int i = 0;
    int bound = SPECIES.loopBound(length);
    FloatVector vGL = FloatVector.broadcast(SPECIES, gL);
    FloatVector vGR = FloatVector.broadcast(SPECIES, gR);

    for (; i < bound; i += SPECIES.length()) {
      var vIn = FloatVector.fromArray(SPECIES, inBuf, i);

      // Left output
      var vOutL = vIn.mul(vGL);
      vOutL.intoArray(multiBlockCache[0], i);
      if (buffer != null) vOutL.intoArray(buffer, offset + i);

      // Right output
      var vOutR = vIn.mul(vGR);
      vOutR.intoArray(multiBlockCache[1], i);
    }

    // Tail
    for (; i < length; i++) {
      float val = inBuf[i];
      multiBlockCache[0][i] = val * gL;
      multiBlockCache[1][i] = val * gR;
      if (buffer != null) buffer[offset + i] = multiBlockCache[0][i];
    }

    // Update state
    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = systemTime + length - 1;
    lastOutChannels[0] = multiBlockCache[0][length - 1];
    lastOutChannels[1] = multiBlockCache[1][length - 1];
    lastOut = lastOutChannels[0];
    left.lastOut = lastOutChannels[0];
    right.lastOut = lastOutChannels[1];
  }

  @Override
  protected void computeStereo(float input, long systemTime) {
    lastOutChannels[0] = input * gL;
    lastOutChannels[1] = input * gR;
    left.lastOut = lastOutChannels[0];
    right.lastOut = lastOutChannels[1];
  }
}
