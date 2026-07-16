package org.chuck.audio.backend;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.chuck.audio.AudioSampleFormat;

/**
 * Concrete {@link AudioBackendStream} over a pair of open, already-{@code hw_params}-negotiated
 * ALSA PCM handles (see {@link AlsaBackend#openStream}). Purely passive and caller-driven, same
 * shape as {@link JavaSoundBackendStream}: {@code snd_pcm_writei} in blocking mode behaves like
 * {@code SourceDataLine.write()}, so no dedicated I/O thread or FFM upcall is needed.
 *
 * <p>Unlike {@link JavaSoundBackendStream}, the encode/decode scratch buffers are pre-allocated
 * (grown only if a caller ever requests more than the negotiated buffer size) rather than allocated
 * fresh on every {@link #writeOutput}/{@link #readInput} call.
 */
public class AlsaBackendStream implements AudioBackendStream {
  private static final Logger logger = Logger.getLogger(AlsaBackendStream.class.getName());
  private static final int MAX_WRITE_RECOVERY_ATTEMPTS = 4;

  private final AudioStreamConfig config;
  private final MemorySegment playbackPcm;
  private final MemorySegment capturePcm; // nullable
  private final int actualSampleRate;
  private final int effectiveBufferSize;
  private final int outputLatencySamples;
  private final int inputLatencySamples;

  private final Arena arena = Arena.ofShared();
  private MemorySegment playbackScratch;
  private int playbackScratchBytes;
  private MemorySegment captureScratch;
  private int captureScratchBytes;

  private final AtomicLong underrunCount = new AtomicLong();
  private final AtomicLong overflowCount = new AtomicLong();
  private volatile boolean running = false;

  AlsaBackendStream(
      AudioStreamConfig config,
      MemorySegment playbackPcm,
      MemorySegment capturePcm,
      int actualSampleRate,
      int effectiveBufferSize,
      int outputLatencySamples,
      int inputLatencySamples) {
    this.config = config;
    this.playbackPcm = playbackPcm;
    this.capturePcm = capturePcm;
    this.actualSampleRate = actualSampleRate;
    this.effectiveBufferSize = effectiveBufferSize;
    this.outputLatencySamples = outputLatencySamples;
    this.inputLatencySamples = inputLatencySamples;

    ensurePlaybackScratch(
        effectiveBufferSize * config.numOutputChannels() * config.sampleFormat().bytesPerSample);
    if (capturePcm != null) {
      ensureCaptureScratch(effectiveBufferSize * config.numInputChannels() * 2);
    }
  }

  @Override
  public void start() {
    running = true;
  }

  @Override
  public void stop() {
    if (!running) return;
    running = false;
    dropQuietly(playbackPcm);
    if (capturePcm != null) dropQuietly(capturePcm);
  }

  @Override
  public boolean isRunning() {
    return running;
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
    if (capturePcm == null || !running || length <= 0) return 0;
    int channels = config.numInputChannels();
    long framesRequested = length / channels;
    if (framesRequested <= 0) return 0;

    long avail;
    try {
      avail = (long) AlsaNative.snd_pcm_avail_update.invokeExact(capturePcm);
    } catch (Throwable t) {
      return 0;
    }
    if (avail < 0) {
      overflowCount.incrementAndGet();
      recoverQuietly(capturePcm, (int) avail);
      return 0;
    }

    long framesToRead = Math.min(avail, framesRequested);
    if (framesToRead <= 0) return 0;

    ensureCaptureScratch((int) (framesToRead * channels * 2));
    try {
      long rc =
          (long) AlsaNative.snd_pcm_readi.invokeExact(capturePcm, captureScratch, framesToRead);
      if (rc < 0) {
        // Nonblocking capture contract: treat EAGAIN/transient errors as "0 frames this cycle",
        // not a hard failure - matches JavaSoundBackendStream's "grab what's ready, else 0".
        recoverQuietly(capturePcm, (int) rc);
        return 0;
      }
      int shortsRead = (int) (rc * channels);
      decodeS16(captureScratch, buffer, offset, shortsRead);
      return shortsRead;
    } catch (Throwable t) {
      return 0;
    }
  }

  @Override
  public void writeOutput(float[] buffer, int offset, int length) {
    if (playbackPcm == null || !running || length <= 0) return;
    int channels = config.numOutputChannels();
    AudioSampleFormat fmt = config.sampleFormat();
    int bps = fmt.bytesPerSample;

    ensurePlaybackScratch(length * bps);
    encode(buffer, offset, length, playbackScratch, fmt);

    long framesTotal = length / channels;
    long framesWritten = 0;
    int attempts = 0;
    while (framesWritten < framesTotal && attempts < MAX_WRITE_RECOVERY_ATTEMPTS) {
      try {
        MemorySegment chunk = playbackScratch.asSlice(framesWritten * channels * bps);
        long rc =
            (long)
                AlsaNative.snd_pcm_writei.invokeExact(
                    playbackPcm, chunk, framesTotal - framesWritten);
        if (rc >= 0) {
          framesWritten += rc;
        } else {
          underrunCount.incrementAndGet();
          attempts++;
          if (!recoverQuietly(playbackPcm, (int) rc)) break;
        }
      } catch (Throwable t) {
        break;
      }
    }
  }

  @Override
  public void close() {
    stop();
    closeQuietly(playbackPcm);
    if (capturePcm != null) closeQuietly(capturePcm);
    arena.close();
  }

  // ── ALSA plumbing ────────────────────────────────────────────────────────

  private void ensurePlaybackScratch(int bytes) {
    if (playbackScratch == null || playbackScratchBytes < bytes) {
      playbackScratch = arena.allocate(bytes);
      playbackScratchBytes = bytes;
    }
  }

  private void ensureCaptureScratch(int bytes) {
    if (captureScratch == null || captureScratchBytes < bytes) {
      captureScratch = arena.allocate(bytes);
      captureScratchBytes = bytes;
    }
  }

  /** Returns true if recovery succeeded (caller may retry), false to give up for this cycle. */
  private boolean recoverQuietly(MemorySegment pcm, int errnum) {
    try {
      int rc = (int) AlsaNative.snd_pcm_recover.invokeExact(pcm, errnum, 1);
      if (rc != 0) {
        logger.log(Level.FINE, "[AlsaBackendStream] snd_pcm_recover: " + AlsaNative.strerror(rc));
      }
      return rc == 0;
    } catch (Throwable t) {
      return false;
    }
  }

  private void dropQuietly(MemorySegment pcm) {
    try {
      int rc = (int) AlsaNative.snd_pcm_drop.invokeExact(pcm);
      if (rc != 0) {
        logger.log(Level.FINE, "[AlsaBackendStream] snd_pcm_drop: " + AlsaNative.strerror(rc));
      }
    } catch (Throwable t) {
      logger.log(Level.FINE, "[AlsaBackendStream] snd_pcm_drop threw: " + t);
    }
  }

  private void closeQuietly(MemorySegment pcm) {
    try {
      int rc = (int) AlsaNative.snd_pcm_close.invokeExact(pcm);
      if (rc != 0) {
        logger.log(Level.FINE, "[AlsaBackendStream] snd_pcm_close: " + AlsaNative.strerror(rc));
      }
    } catch (Throwable t) {
      logger.log(Level.FINE, "[AlsaBackendStream] snd_pcm_close threw: " + t);
    }
  }

  // ── Sample encode/decode ─────────────────────────────────────────────────

  private static void encode(
      float[] src, int srcOffset, int count, MemorySegment dst, AudioSampleFormat fmt) {
    for (int i = 0; i < count; i++) {
      float sample = src[srcOffset + i];
      float clamp = Math.max(-1f, Math.min(1f, sample));
      switch (fmt) {
        case INT16 ->
            dst.set(
                ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN),
                (long) i * 2,
                (short) (clamp * 32767f));
        case INT24 -> {
          int s24 = (int) (clamp * 8388607f);
          long off = (long) i * 3;
          dst.set(ValueLayout.JAVA_BYTE, off, (byte) (s24 & 0xFF));
          dst.set(ValueLayout.JAVA_BYTE, off + 1, (byte) ((s24 >> 8) & 0xFF));
          dst.set(ValueLayout.JAVA_BYTE, off + 2, (byte) ((s24 >> 16) & 0xFF));
        }
        case INT32 ->
            dst.set(
                ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN),
                (long) i * 4,
                (int) (clamp * (float) Integer.MAX_VALUE));
        case FLOAT32 ->
            // No clamping - float output can carry headroom beyond +-1, matching ChuckAudio's
            // own writeSample() convention for FLOAT32.
            dst.set(
                ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), (long) i * 4, sample);
      }
    }
  }

  private static void decodeS16(MemorySegment src, short[] dst, int dstOffset, int count) {
    for (int i = 0; i < count; i++) {
      dst[dstOffset + i] =
          src.get(ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), (long) i * 2);
    }
  }
}
