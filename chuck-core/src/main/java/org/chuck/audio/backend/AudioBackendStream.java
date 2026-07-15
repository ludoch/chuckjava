package org.chuck.audio.backend;

/**
 * Represents an open, managed audio stream (playback/capture) provided by an {@link AudioBackend}.
 */
public interface AudioBackendStream extends AutoCloseable {
  /** Starts audio I/O processing. */
  void start();

  /** Stops audio I/O processing. */
  void stop();

  /** Whether the stream is actively running and processing audio. */
  boolean isRunning();

  /** The negotiated, actual sample rate in Hertz (e.g. 44100, 48000). */
  int getActualSampleRate();

  /** The effective buffer size (in samples per channel) negotiated by the driver. */
  int getEffectiveBufferSize();

  /** Output latency in samples. */
  int getOutputLatencySamples();

  /** Input latency in samples. */
  int getInputLatencySamples();

  /** Total output underruns detected on this stream. */
  long getUnderrunCount();

  /** Total input overflows detected on this stream. */
  long getOverflowCount();

  /** Reads from ADC into short buffer (interleaved INT16). Returns samples read. */
  int readInput(short[] buffer, int offset, int length);

  /** Writes to DAC from float buffer (interleaved). */
  void writeOutput(float[] buffer, int offset, int length);

  @Override
  void close();
}
