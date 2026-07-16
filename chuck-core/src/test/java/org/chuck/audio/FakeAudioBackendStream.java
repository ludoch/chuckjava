package org.chuck.audio;

import java.util.concurrent.atomic.AtomicLong;
import org.chuck.audio.backend.AudioBackendStream;
import org.chuck.audio.backend.AudioStreamConfig;

/**
 * In-memory {@link AudioBackendStream} test double. {@link #writeOutput} accumulates RMS/peak stats
 * (same idiom {@code DslExamplesTest} uses for its {@code maxRms > 0.001} assertions) instead of
 * touching a real device, and {@link #readInput} returns silence. {@link
 * #artificialWriteDelayNanos} lets a test force {@link ChuckAudio}'s wall-clock drift/underrun
 * detection to fire, since that logic times how long each buffer cycle actually takes regardless of
 * which backend is behind it.
 */
public class FakeAudioBackendStream implements AudioBackendStream {
  private final AudioStreamConfig config;
  private volatile boolean running = false;

  private final AtomicLong underrunCount = new AtomicLong();
  private final AtomicLong overflowCount = new AtomicLong();
  private final AtomicLong writeCount = new AtomicLong();
  private final AtomicLong totalFramesWritten = new AtomicLong();

  private volatile double lastSumSquares = 0;
  private volatile int lastSampleCount = 0;
  private volatile float lastPeak = 0f;
  private volatile double maxRmsSeen = 0;

  /** If > 0, {@link #writeOutput} sleeps this long before returning - see class javadoc. */
  public volatile long artificialWriteDelayNanos = 0;

  public FakeAudioBackendStream(AudioStreamConfig config) {
    this.config = config;
  }

  @Override
  public void start() {
    running = true;
  }

  @Override
  public void stop() {
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getActualSampleRate() {
    return config.sampleRate();
  }

  @Override
  public int getEffectiveBufferSize() {
    return config.bufferSize();
  }

  @Override
  public int getOutputLatencySamples() {
    return config.bufferSize() * config.numBuffers();
  }

  @Override
  public int getInputLatencySamples() {
    return config.numInputChannels() > 0 ? config.bufferSize() * config.numBuffers() : 0;
  }

  @Override
  public long getUnderrunCount() {
    return underrunCount.get();
  }

  @Override
  public long getOverflowCount() {
    return overflowCount.get();
  }

  @Override
  public int readInput(short[] buffer, int offset, int length) {
    if (!running) return 0;
    java.util.Arrays.fill(buffer, offset, offset + length, (short) 0);
    return length;
  }

  @Override
  public void writeOutput(float[] buffer, int offset, int length) {
    if (artificialWriteDelayNanos > 0) {
      try {
        Thread.sleep(
            artificialWriteDelayNanos / 1_000_000, (int) (artificialWriteDelayNanos % 1_000_000));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    double sumSq = 0;
    float peak = 0;
    for (int i = 0; i < length; i++) {
      float s = buffer[offset + i];
      sumSq += (double) s * s;
      float abs = Math.abs(s);
      if (abs > peak) peak = abs;
    }
    lastSumSquares = sumSq;
    lastSampleCount = length;
    lastPeak = peak;
    double rms = length > 0 ? Math.sqrt(sumSq / length) : 0;
    if (rms > maxRmsSeen) maxRmsSeen = rms;
    writeCount.incrementAndGet();
    totalFramesWritten.addAndGet(
        config.numOutputChannels() > 0 ? length / config.numOutputChannels() : 0);
  }

  @Override
  public void close() {
    stop();
  }

  // ── Test inspection hooks ────────────────────────────────────────────────

  /** Peak-hold-decayed RMS of the most recent {@link #writeOutput} call. */
  public double lastRms() {
    return lastSampleCount > 0 ? Math.sqrt(lastSumSquares / lastSampleCount) : 0;
  }

  /** Highest per-buffer RMS observed across every {@link #writeOutput} call so far. */
  public double maxRmsSeen() {
    return maxRmsSeen;
  }

  public float lastPeak() {
    return lastPeak;
  }

  public long writeCount() {
    return writeCount.get();
  }

  public long totalFramesWritten() {
    return totalFramesWritten.get();
  }

  /** Test-only hook: lets a test simulate a driver-reported xrun independent of timing. */
  public void injectUnderrun() {
    underrunCount.incrementAndGet();
  }

  public void injectOverflow() {
    overflowCount.incrementAndGet();
  }
}
