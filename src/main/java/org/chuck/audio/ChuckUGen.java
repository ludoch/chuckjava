package org.chuck.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

/** Base class for Unit Generators. */
public abstract class ChuckUGen extends ChuckObject {
  protected final List<ChuckUGen> sources = new ArrayList<>();
  protected final List<ChuckUGen> targets = new ArrayList<>();
  protected final ReentrantLock ugenLock = new ReentrantLock();

  @SuppressWarnings("unused") // Used via introspection in ChucK scripts
  public float lastOut = 0.0f;

  @SuppressWarnings("unused") // Used via introspection in ChucK scripts
  public float gain = 1.0f;

  protected long lastTickTime = -1;
  protected boolean isTicking = false;

  protected float[] blockCache;
  protected long blockStartTime = -1;
  protected int blockLength = 0;

  protected int numInputs = 1;
  protected int numOutputs = 1;
  protected ChuckUGen[] inputChannels;
  protected ChuckUGen[] outputChannels;

  public ChuckUGen(ChuckType type) {
    super(type);
    // Auto-register with current shred if running in a VM context
    try {
      org.chuck.core.ChuckShred current = org.chuck.core.ChuckShred.CURRENT_SHRED.get();
      if (current != null) {
        current.registerUGen(this);
      }
    } catch (Exception ignored) {
    }
  }

  public ChuckUGen() {
    super(new ChuckType("UGen", ChuckType.OBJECT, 0, 0));
    // Auto-register with current shred if running in a VM context
    try {
      org.chuck.core.ChuckShred current = org.chuck.core.ChuckShred.CURRENT_SHRED.get();
      if (current != null) {
        current.registerUGen(this);
      }
    } catch (Exception ignored) {
    }
  }

  public void addSource(ChuckUGen src) {
    if (src != null) {
      ugenLock.lock();
      try {
        if (!sources.contains(src)) {
          sources.add(src);
          invalidateVmGraph();
        }
      } finally {
        ugenLock.unlock();
      }
    }
  }

  public void removeSource(ChuckUGen src) {
    if (src != null) {
      ugenLock.lock();
      try {
        if (sources.remove(src)) {
          invalidateVmGraph();
        }
      } finally {
        ugenLock.unlock();
      }
    }
  }

  private void invalidateVmGraph() {
    if (org.chuck.core.ChuckVM.CURRENT_VM.isBound()) {
      org.chuck.core.ChuckVM.CURRENT_VM.get().invalidateGraph();
    }
  }

  public void chuckTo(ChuckUGen target) {
    if (target != null) {
      target.addSource(this);
      ugenLock.lock();
      try {
        if (!targets.contains(target)) {
          targets.add(target);
        }
      } finally {
        ugenLock.unlock();
      }
    }
  }

  /**
   * Fluent API for ChucK-style connections in Java. Usage: osc.chuck(filter).chuck(dac);
   *
   * @return the target UGen for chaining
   */
  public <T extends ChuckUGen> T chuck(T target) {
    chuckTo(target);
    return target;
  }

  public void unchuck(ChuckUGen target) {
    if (target != null) {
      target.removeSource(this);
      ugenLock.lock();
      try {
        targets.remove(target);
      } finally {
        ugenLock.unlock();
      }
    }
  }

  /** Disconnect from all targets. */
  public void unchuckAll() {
    List<ChuckUGen> copy;
    ugenLock.lock();
    try {
      copy = new ArrayList<>(targets);
      targets.clear();
    } finally {
      ugenLock.unlock();
    }
    for (ChuckUGen target : copy) {
      target.removeSource(this);
    }
  }

  public void disconnectAll() {
    unchuckAll();
  }

  public float tick(long systemTime) {
    if (systemTime != -1 && systemTime == lastTickTime) {
      return lastOut;
    }

    // Check if this sample is already in our block cache
    if (systemTime != -1
        && blockLength > 0
        && systemTime >= blockStartTime
        && systemTime < blockStartTime + blockLength) {
      int idx = (int) (systemTime - blockStartTime);
      lastOut = blockCache[idx];
      lastTickTime = systemTime;
      return lastOut;
    }

    if (isTicking) {
      return lastOut;
    }
    isTicking = true;

    try {
      float sum = 0.0f;
      List<ChuckUGen> srcs = getSources();
      for (ChuckUGen src : srcs) {
        sum += src.tick(systemTime);
      }

      lastOut = compute(sum, systemTime) * gain;
      lastTickTime = systemTime;
      blockStartTime = systemTime;
      blockLength = 0; // When computing scalar, reset block window to prevent stale cache hits
      return lastOut;

    } finally {
      isTicking = false;
    }
  }

  public float tick() {
    return tick(-1);
  }

  public float tick(float manualInput) {
    return tick(manualInput, -1);
  }

  public float tick(float manualInput, long systemTime) {
    if (systemTime != -1 && systemTime == lastTickTime) {
      return lastOut;
    }

    // Check if this sample is already in our block cache
    if (systemTime != -1
        && blockLength > 0
        && systemTime >= blockStartTime
        && systemTime < blockStartTime + blockLength) {
      int idx = (int) (systemTime - blockStartTime);
      lastOut = blockCache[idx];
      lastTickTime = systemTime;
      return lastOut;
    }

    lastOut = compute(manualInput, systemTime) * gain;
    lastTickTime = systemTime;
    blockStartTime = systemTime;
    blockLength = 0;
    return lastOut;
  }

  /** ChucK-style: ugen.next(val) sets manual input for this tick */
  public double next(double val) {
    lastOut = compute((float) val, -1) * gain;
    return val;
  }

  protected abstract float compute(float input, long systemTime);

  public void setGain(float gain) {
    this.gain = gain;
  }

  public float getGain() {
    return gain;
  }

  /** ChucK-style gain(val) setter — called as p.gain(0.5) */
  public double gain(double val) {
    this.gain = (float) val;
    return val;
  }

  /** ChucK-style gain() getter — called as p.gain() */
  public double gain() {
    return this.gain;
  }

  public float getLastOut() {
    return lastOut;
  }

  public float[] getBlockCache() {
    return blockCache;
  }

  /** ChucK-style: ugen.last() returns most recent sample */
  public float last() {
    return lastOut;
  }

  /** Returns the most recent sample for a specific output channel. */
  public float getChannelLastOut(int i) {
    // ChucK behavior: mono UGens provide their output to all requested channels.
    // Stereo/multi-channel UGens provide specific channels.
    if (numOutputs == 1) return lastOut;
    if (i >= 0 && i < numOutputs) return lastOut; // Fallback for single-out multi-channel proxies
    return 0.0f;
  }

  /** New method to support bit-exact multi-channel block caching. */
  public float getChannelLastOut(int i, long systemTime) {
    if (systemTime != -1
        && blockLength > 0
        && systemTime >= blockStartTime
        && systemTime < blockStartTime + blockLength) {
      return getChannelLastOut(i); // For mono, it's already in lastOut
    }
    return getChannelLastOut(i);
  }

  public List<ChuckUGen> getSources() {
    ugenLock.lock();
    try {
      return new ArrayList<>(sources);
    } finally {
      ugenLock.unlock();
    }
  }

  public int getNumSources() {
    ugenLock.lock();
    try {
      return sources.size();
    } finally {
      ugenLock.unlock();
    }
  }

  public int isConnectedTo(ChuckUGen target) {
    return isConnectedTo(target, 0);
  }

  private int isConnectedTo(ChuckUGen target, int depth) {
    if (depth > 5) return 0;
    ugenLock.lock();
    List<ChuckUGen> targetList;
    try {
      if (targets.contains(target)) return 1;
      targetList = new ArrayList<>(targets);
    } finally {
      ugenLock.unlock();
    }
    // Check if any of our targets connects to the target (recursive)
    for (ChuckUGen t : targetList) {
      if (t.isConnectedTo(target, depth + 1) == 1) return 1;
    }
    // Also check input channels of target
    if (target != null && target.inputChannels != null) {
      for (ChuckUGen in : target.inputChannels) {
        if (in != null && this.isConnectedTo(in, depth + 1) == 1) return 1;
      }
    }
    return 0;
  }

  public void clearSources() {
    List<ChuckUGen> copy;
    ugenLock.lock();
    try {
      copy = new ArrayList<>(sources);
      sources.clear();
    } finally {
      ugenLock.unlock();
    }
    for (ChuckUGen src : copy) {
      src.ugenLock.lock();
      try {
        src.targets.remove(this);
      } finally {
        src.ugenLock.unlock();
      }
    }
  }

  public void tick(float[] buffer) {
    tick(buffer, 0, buffer.length, -1);
  }

  public void tick(float[] buffer, int offset, int length, long systemTime) {
    tick(buffer, offset, length, systemTime, null);
  }

  public void tick(float[] buffer, int offset, int length, long systemTime, float[] manualInput) {
    // If we've already ticked this block for this exact time, copy from cache
    if (systemTime != -1
        && systemTime == blockStartTime
        && blockCache != null
        && blockLength >= length) {
      if (buffer != null) {
        System.arraycopy(blockCache, 0, buffer, offset, length);
      }
      return;
    }

    // Allocate or resize cache if needed
    if (blockCache == null || blockCache.length < length) {
      blockCache = new float[length];
    }

    // Default implementation: compute sample-by-sample
    for (int i = 0; i < length; i++) {
      float in = (manualInput != null) ? manualInput[i] : 0.0f;
      float out;
      if (manualInput != null) {
        out = tick(in, systemTime == -1 ? -1 : systemTime + i);
      } else {
        out = tick(systemTime == -1 ? -1 : systemTime + i);
      }
      blockCache[i] = out;
      if (buffer != null) {
        buffer[offset + i] = out;
      }
    }

    blockStartTime = systemTime;
    blockLength = length;
    if (length > 0) {
      lastOut = blockCache[length - 1];
      lastTickTime = systemTime + length - 1;
    }
  }

  public int getNumInputs() {
    return numInputs;
  }

  public int getNumOutputs() {
    return numOutputs;
  }

  public ChuckUGen getInputChannel(int i) {
    return (inputChannels != null && i < inputChannels.length) ? inputChannels[i] : this;
  }

  public ChuckUGen getOutputChannel(int i) {
    return (outputChannels != null && i < outputChannels.length) ? outputChannels[i] : this;
  }
}
