package org.chuck.audio;

/**
 * Sample formats supported by ChuckAudio, mirroring RtAudio's format flags.
 *
 * <p>INT16 is the universal fallback — every javax.sound driver supports it. Higher-resolution
 * formats (INT24, INT32, FLOAT32) are attempted first and silently fall back to INT16 on {@code
 * LineUnavailableException}.
 */
public enum AudioSampleFormat {
  /** 16-bit signed integer PCM — 2 bytes/sample, universal support. */
  INT16(2, 16),
  /** 24-bit signed integer PCM — 3 bytes/sample, standard for pro audio interfaces. */
  INT24(3, 24),
  /** 32-bit signed integer PCM — 4 bytes/sample, highest integer resolution. */
  INT32(4, 32),
  /** 32-bit IEEE 754 float PCM — 4 bytes/sample, avoids quantisation in the output path. */
  FLOAT32(4, 32);

  /** Bytes consumed per mono sample frame. */
  public final int bytesPerSample;

  /** Bit depth (informational). */
  public final int bitDepth;

  AudioSampleFormat(int bytesPerSample, int bitDepth) {
    this.bytesPerSample = bytesPerSample;
    this.bitDepth = bitDepth;
  }

  /** Build the matching {@link javax.sound.sampled.AudioFormat}. */
  public javax.sound.sampled.AudioFormat toJavaAudioFormat(float sampleRate, int channels) {
    return switch (this) {
      case INT16 ->
          new javax.sound.sampled.AudioFormat(
              javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
              sampleRate,
              16,
              channels,
              channels * 2,
              sampleRate,
              false);
      case INT24 ->
          new javax.sound.sampled.AudioFormat(
              javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
              sampleRate,
              24,
              channels,
              channels * 3,
              sampleRate,
              false);
      case INT32 ->
          new javax.sound.sampled.AudioFormat(
              javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
              sampleRate,
              32,
              channels,
              channels * 4,
              sampleRate,
              false);
      case FLOAT32 ->
          new javax.sound.sampled.AudioFormat(
              javax.sound.sampled.AudioFormat.Encoding.PCM_FLOAT,
              sampleRate,
              32,
              channels,
              channels * 4,
              sampleRate,
              false);
    };
  }
}
