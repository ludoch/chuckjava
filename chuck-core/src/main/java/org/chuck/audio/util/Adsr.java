package org.chuck.audio.util;

import static org.chuck.audio.VectorAudio.SPECIES;

import java.util.List;
import jdk.incubator.vector.FloatVector;
import org.chuck.audio.ChuckUGen;

/** An Attack, Decay, Sustain, Release envelope generator. */
public class Adsr extends ChuckUGen {
  public static final int ATTACK = 0;
  public static final int DECAY = 1;
  public static final int SUSTAIN = 2;
  public static final int RELEASE = 3;
  public static final int DONE = 4;

  public enum State {
    ATTACK_ENUM(ATTACK),
    DECAY_ENUM(DECAY),
    SUSTAIN_ENUM(SUSTAIN),
    RELEASE_ENUM(RELEASE),
    DONE_ENUM(DONE);

    final int value;

    State(int v) {
      this.value = v;
    }
  }

  private State state = State.DONE_ENUM;

  @SuppressWarnings("unused") // Used via introspection in ChucK scripts
  private float currentLevel = 0.0f;

  private final float sampleRate;

  // Default values (seconds)
  private float attackTime = 0.005f;
  private float decayTime = 0.005f;
  private float sustainLevel = 0.5f;
  private float releaseTime = 0.005f;

  // Increments per sample
  private float attackInc;
  private float decayInc;
  private float releaseInc;

  public Adsr(float sampleRate) {
    this(sampleRate, true);
  }

  public Adsr(float sampleRate, boolean autoRegister) {
    super(autoRegister);
    this.sampleRate = sampleRate;
    updateIncrements();
  }

  public int state() {
    return state.value;
  }

  public int getState() {
    return state.value;
  }

  public void set(float attack, float decay, float sustain, float release) {
    this.attackTime = attack;
    this.decayTime = decay;
    this.sustainLevel = sustain;
    this.releaseTime = release;
    updateIncrements();
  }

  public void set(
      double attackSamples, double decaySamples, double sustain, double releaseSamples) {
    this.attackTime = (float) (attackSamples / sampleRate);
    this.decayTime = (float) (decaySamples / sampleRate);
    this.sustainLevel = (float) sustain;
    this.releaseTime = (float) (releaseSamples / sampleRate);
    updateIncrements();
  }

  private void updateIncrements() {
    attackInc = (attackTime > 0) ? 1.0f / (attackTime * sampleRate) : 1.0f;
    decayInc = (decayTime > 0) ? (1.0f - sustainLevel) / (decayTime * sampleRate) : 0.0f;
    releaseInc = (releaseTime > 0) ? sustainLevel / (releaseTime * sampleRate) : sustainLevel;
  }

  public void keyOn() {
    state = State.ATTACK_ENUM;
  }

  public void keyOn(int on) {
    if (on != 0) state = State.ATTACK_ENUM;
    else state = State.RELEASE_ENUM;
  }

  public void keyOff() {
    state = State.RELEASE_ENUM;
    releaseInc = (releaseTime > 0) ? currentLevel / (releaseTime * sampleRate) : currentLevel;
  }

  public void keyOff(int off) {
    if (off != 0) {
      state = State.RELEASE_ENUM;
      releaseInc = (releaseTime > 0) ? currentLevel / (releaseTime * sampleRate) : currentLevel;
    } else {
      state = State.ATTACK_ENUM;
    }
  }

  public double attackTime() {
    return attackTime * sampleRate;
  }

  public double decayTime() {
    return decayTime * sampleRate;
  }

  public double sustainLevel() {
    return sustainLevel;
  }

  public double releaseTime() {
    return releaseTime * sampleRate;
  }

  public double getAttackTime() {
    return attackTime * sampleRate;
  }

  public double getDecayTime() {
    return decayTime * sampleRate;
  }

  public double getSustainLevel() {
    return sustainLevel;
  }

  public double getReleaseTime() {
    return releaseTime * sampleRate;
  }

  @Override
  protected float compute(float input, long systemTime) {
    update(systemTime);
    if (getNumSources() == 0) {
      return currentLevel;
    }
    return input * currentLevel;
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

    int i = 0;
    // Optimization: if we are in SUSTAIN or DONE, the gain is constant
    if (state == State.SUSTAIN_ENUM || state == State.DONE_ENUM) {
      float gainVal = (state == State.SUSTAIN_ENUM) ? sustainLevel : 0.0f;
      java.util.Arrays.fill(blockCache, 0, length, 0.0f);

      // Vectorized summing from all sources
      List<ChuckUGen> srcs = getSources();
      if (!srcs.isEmpty()) {
        for (ChuckUGen src : srcs) {
          float[] temp = new float[length];
          src.tick(temp, 0, length, systemTime);

          // SIMD Addition: blockCache += temp
          int j = 0;
          int bound = SPECIES.loopBound(length);
          for (; j < bound; j += SPECIES.length()) {
            FloatVector vSum = FloatVector.fromArray(SPECIES, blockCache, j);
            FloatVector vSrc = FloatVector.fromArray(SPECIES, temp, j);
            vSum.add(vSrc).intoArray(blockCache, j);
          }
          for (; j < length; j++) blockCache[j] += temp[j];
        }
      }

      int bound = SPECIES.loopBound(length);
      FloatVector vGain = FloatVector.broadcast(SPECIES, gainVal);
      for (; i < bound; i += SPECIES.length()) {
        var vIn = FloatVector.fromArray(SPECIES, blockCache, i);
        var vOut = vIn.mul(vGain);
        vOut.intoArray(blockCache, i);
        if (buffer != null) {
          vOut.intoArray(buffer, offset + i);
        }
      }
      for (; i < length; i++) {
        float out = blockCache[i] * gainVal;
        blockCache[i] = out;
        if (buffer != null) {
          buffer[offset + i] = out;
        }
      }
      currentLevel = gainVal;
    } else {
      // Otherwise, fallback to scalar for state-transition accuracy
      for (; i < length; i++) {
        float out = tick(systemTime == -1 ? -1 : systemTime + i);
        blockCache[i] = out;
        if (buffer != null) {
          buffer[offset + i] = out;
        }
      }
    }

    blockStartTime = systemTime;
    blockLength = length;
    lastTickTime = systemTime + length - 1;
    if (length > 0) {
      lastOut = blockCache[length - 1];
    }
  }

  private void update(long systemTime) {
    if (systemTime != -1 && systemTime == lastTickTime) return;
    lastTickTime = systemTime;

    switch (state) {
      case ATTACK_ENUM -> {
        currentLevel += attackInc;
        if (currentLevel >= 1.0f) {
          currentLevel = 1.0f;
          state = State.DECAY_ENUM;
        }
      }
      case DECAY_ENUM -> {
        currentLevel -= decayInc;
        if (currentLevel <= sustainLevel) {
          currentLevel = sustainLevel;
          state = State.SUSTAIN_ENUM;
        }
      }
      case SUSTAIN_ENUM -> currentLevel = sustainLevel;
      case RELEASE_ENUM -> {
        currentLevel -= releaseInc;
        if (currentLevel <= 0.0f) {
          currentLevel = 0.0f;
          state = State.DONE_ENUM;
        }
      }
      case DONE_ENUM -> currentLevel = 0.0f;
    }
  }

  public float getCurrentLevel() {
    return currentLevel;
  }
}
