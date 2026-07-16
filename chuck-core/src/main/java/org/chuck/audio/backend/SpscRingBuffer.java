package org.chuck.audio.backend;

import java.util.Arrays;

/**
 * Lock-free single-producer/single-consumer ring buffer of interleaved float samples.
 *
 * <p>Connects a normal Java thread (the producer — e.g. {@link ChuckAudio}'s engine loop calling
 * {@code writeOutput()}) to a native-owned real-time callback thread (the consumer — e.g. a JACK
 * process callback or CoreAudio render callback invoked via an FFM upcall) without locks. Safe for
 * exactly one writer thread and one reader thread; not safe for multiple producers or consumers.
 *
 * <p>{@link #write} silently drops (returns fewer frames than requested) rather than blocking when
 * full; {@link #read} zero-fills rather than blocking when the ring doesn't yet have enough data —
 * both are the correct behavior for a real-time audio callback, which must never block.
 */
final class SpscRingBuffer {
  private final float[] buf;
  private final int capacityFrames;
  private final int channels;

  // Monotonically increasing frame counts. writePos is written only by the producer thread,
  // readPos only by the consumer thread; each is read (but never written) by the other side.
  // volatile gives the happens-before publication needed so buffer contents written before a
  // writePos update are visible to a consumer that observes the new writePos, and vice versa.
  private volatile long writePos = 0;
  private volatile long readPos = 0;

  SpscRingBuffer(int capacityFrames, int channels) {
    if (capacityFrames <= 0) throw new IllegalArgumentException("capacityFrames must be > 0");
    if (channels <= 0) throw new IllegalArgumentException("channels must be > 0");
    this.capacityFrames = capacityFrames;
    this.channels = channels;
    this.buf = new float[capacityFrames * channels];
  }

  /**
   * Producer side. Writes up to {@code frames} interleaved frames from {@code src[offset...]}.
   * Returns the number of frames actually written — less than requested if the ring is full; the
   * caller should treat a short write as an overflow condition.
   */
  int write(float[] src, int offset, int frames) {
    long occupied = writePos - readPos; // may be a stale (over-)estimate; safe, see class javadoc
    int room = (int) Math.max(0, capacityFrames - occupied);
    int toWrite = Math.min(frames, room);
    for (int i = 0; i < toWrite; i++) {
      int frameIdx = (int) ((writePos + i) % capacityFrames);
      System.arraycopy(src, offset + i * channels, buf, frameIdx * channels, channels);
    }
    writePos += toWrite;
    return toWrite;
  }

  /**
   * Consumer side. Reads up to {@code frames} interleaved frames into {@code dst[dstOffset...]}.
   * Any frames not yet available (ring has less data than requested — an underrun) are zero-filled
   * rather than left unwritten. Returns the number of frames that were genuinely available (i.e.
   * not zero-filled) — the caller should treat a short read as an underrun condition.
   */
  int read(float[] dst, int dstOffset, int frames) {
    long available = writePos - readPos; // may be a stale (under-)estimate; safe, see class javadoc
    int toRead = (int) Math.max(0, Math.min(frames, available));
    for (int i = 0; i < toRead; i++) {
      int frameIdx = (int) ((readPos + i) % capacityFrames);
      System.arraycopy(buf, frameIdx * channels, dst, dstOffset + i * channels, channels);
    }
    if (toRead < frames) {
      Arrays.fill(dst, dstOffset + toRead * channels, dstOffset + frames * channels, 0f);
    }
    readPos += toRead;
    return toRead;
  }

  /**
   * Frames currently buffered (best-effort snapshot; only exact when called from either thread
   * about its own side).
   */
  long framesAvailable() {
    return Math.max(0, writePos - readPos);
  }

  int capacityFrames() {
    return capacityFrames;
  }

  int channels() {
    return channels;
  }
}
