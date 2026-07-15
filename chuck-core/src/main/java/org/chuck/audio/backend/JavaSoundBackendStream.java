package org.chuck.audio.backend;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import javax.sound.sampled.*;
import org.chuck.audio.AudioSampleFormat;

/**
 * Concrete {@link AudioBackendStream} wrapping JavaSound {@link SourceDataLine} and {@link
 * TargetDataLine}.
 */
public class JavaSoundBackendStream implements AudioBackendStream {
  private static final Logger logger = Logger.getLogger(JavaSoundBackendStream.class.getName());

  private final AudioStreamConfig config;
  private final SourceDataLine outputLine;
  private final TargetDataLine inputLine;
  private final int actualSampleRate;
  private final int effectiveBufferSize;
  private final int outputLatencySamples;
  private final int inputLatencySamples;

  private final AtomicLong underrunCount = new AtomicLong();
  private final AtomicLong overflowCount = new AtomicLong();
  private volatile boolean running = false;

  public JavaSoundBackendStream(
      AudioStreamConfig config,
      SourceDataLine outputLine,
      TargetDataLine inputLine,
      int actualSampleRate,
      int effectiveBufferSize,
      int outputLatencySamples,
      int inputLatencySamples) {
    this.config = config;
    this.outputLine = outputLine;
    this.inputLine = inputLine;
    this.actualSampleRate = actualSampleRate;
    this.effectiveBufferSize = effectiveBufferSize;
    this.outputLatencySamples = outputLatencySamples;
    this.inputLatencySamples = inputLatencySamples;
  }

  @Override
  public void start() {
    if (running) return;
    running = true;
    if (outputLine != null) outputLine.start();
    if (inputLine != null) inputLine.start();
  }

  @Override
  public void stop() {
    if (!running) return;
    running = false;
    if (outputLine != null) outputLine.stop();
    if (inputLine != null) inputLine.stop();
  }

  @Override
  public boolean isRunning() {
    return running && (outputLine != null && outputLine.isRunning());
  }

  @Override
  public int getActualSampleRate() {
    return actualSampleRate;
  }

  @Override
  public int getEffectiveBufferSize() {
    return effectiveBufferSize;
  }

  @Override
  public int getOutputLatencySamples() {
    return outputLatencySamples;
  }

  @Override
  public int getInputLatencySamples() {
    return inputLatencySamples;
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
    if (inputLine == null || !running) return 0;
    int bytesNeeded = length * 2;
    int avail = inputLine.available();
    if (avail > bytesNeeded * 2) {
      overflowCount.incrementAndGet();
    }
    if (avail >= bytesNeeded) {
      byte[] raw = new byte[bytesNeeded];
      int read = inputLine.read(raw, 0, bytesNeeded);
      for (int i = 0; i < read / 2; i++) {
        buffer[offset + i] = (short) ((raw[i * 2 + 1] << 8) | (raw[i * 2] & 0xFF));
      }
      return read / 2;
    }
    return 0;
  }

  @Override
  public void writeOutput(float[] buffer, int offset, int length) {
    if (outputLine == null || !running) return;
    AudioSampleFormat fmt = config.sampleFormat();
    int bps = fmt.bytesPerSample;
    byte[] raw = new byte[length * bps];
    for (int i = 0; i < length; i++) {
      float sample = buffer[offset + i];
      float clamp = Math.max(-1f, Math.min(1f, sample));
      int base = i * bps;
      switch (fmt) {
        case INT16 -> {
          short s16 = (short) (clamp * 32767f);
          raw[base] = (byte) (s16 & 0xFF);
          raw[base + 1] = (byte) ((s16 >> 8) & 0xFF);
        }
        case INT24 -> {
          int s24 = (int) (clamp * 8388607f);
          raw[base] = (byte) (s24 & 0xFF);
          raw[base + 1] = (byte) ((s24 >> 8) & 0xFF);
          raw[base + 2] = (byte) ((s24 >> 16) & 0xFF);
        }
        case INT32 -> {
          int s32 = (int) (clamp * (float) Integer.MAX_VALUE);
          raw[base] = (byte) (s32 & 0xFF);
          raw[base + 1] = (byte) ((s32 >> 8) & 0xFF);
          raw[base + 2] = (byte) ((s32 >> 16) & 0xFF);
          raw[base + 3] = (byte) ((s32 >> 24) & 0xFF);
        }
        case FLOAT32 -> {
          int bits = Float.floatToRawIntBits(sample);
          raw[base] = (byte) (bits & 0xFF);
          raw[base + 1] = (byte) ((bits >> 8) & 0xFF);
          raw[base + 2] = (byte) ((bits >> 16) & 0xFF);
          raw[base + 3] = (byte) ((bits >> 24) & 0xFF);
        }
      }
    }
    outputLine.write(raw, 0, raw.length);
  }

  @Override
  public void close() {
    stop();
    if (outputLine != null) {
      outputLine.close();
    }
    if (inputLine != null) {
      inputLine.close();
    }
  }
}
