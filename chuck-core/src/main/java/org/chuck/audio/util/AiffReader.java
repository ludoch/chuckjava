package org.chuck.audio.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility to read AIFF/AIFF-C audio files using pure java.io — no {@code javax.sound} dependency.
 *
 * <p>Reads 8/16/24-bit PCM AIFF and AIFF-C files. AIFF-C with uncompressed PCM is handled; true
 * compressed formats (μ-law, ADPCM) throw an IOException.
 */
public class AiffReader {

  public static class AiffData {
    public final float[][] channels;
    public final int sampleRate;
    public final int bitsPerSample;

    public AiffData(float[][] channels, int sampleRate, int bitsPerSample) {
      this.channels = channels;
      this.sampleRate = sampleRate;
      this.bitsPerSample = bitsPerSample;
    }

    public int frameCount() {
      return channels[0].length;
    }
  }

  public static AiffData read(File file) throws IOException {
    long length = file.length();
    if (length > Integer.MAX_VALUE) throw new IOException("File too large: " + file.length());
    byte[] raw = new byte[(int) length];
    try (FileInputStream fis = new FileInputStream(file)) {
      int offset = 0;
      while (offset < raw.length) {
        int read = fis.read(raw, offset, raw.length - offset);
        if (read < 0) throw new IOException("Unexpected EOF reading " + file);
        offset += read;
      }
    }
    return read(raw);
  }

  public static AiffData read(InputStream stream) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    while (true) {
      int read = stream.read(buf);
      if (read < 0) break;
      baos.write(buf, 0, read);
    }
    return read(baos.toByteArray());
  }

  public static AiffData read(byte[] data) throws IOException {
    ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);

    // FORM header
    checkId(bb, 'F', 'O', 'R', 'M');
    bb.getInt(); // total size (skip)
    int formType = bb.getInt();
    boolean isAifc = (formType == 0x41494643); // "AIFC"
    if (formType != 0x41494646 && formType != 0x41494643) // "AIFF" or "AIFC"
    throw new IOException("Not an AIFF/AIFC file (got " + Integer.toHexString(formType) + ")");

    int numChannels = -1;
    int sampleRate = -1;
    int bitsPerSample = -1;
    byte[] pcmData = null;

    while (bb.remaining() >= 8) {
      int chunkId = bb.getInt();
      int chunkSize = bb.getInt();
      int paddedSize = (chunkSize % 2 == 0) ? chunkSize : chunkSize + 1;
      if (chunkSize < 0 || bb.remaining() < chunkSize) break;

      if (chunkId == 0x434F4D4D) { // "COMM"
        if (chunkSize < 18) throw new IOException("COMM chunk too small: " + chunkSize);
        numChannels = bb.getShort() & 0xFFFF;
        long numSampleFrames = bb.getInt() & 0xFFFFFFFFL;
        bitsPerSample = bb.getShort() & 0xFFFF;

        // Sample rate: 80-bit IEEE 754 extended float (10 bytes) — always at offset 8
        sampleRate = readExtended80(bb);

        // Remaining bytes after the 18-byte standard COMM header
        int commExtra = chunkSize - 18;
        if (isAifc && commExtra >= 4) {
          // AIFC has a 4-byte compression type at offset 18
          byte[] compType = new byte[4];
          bb.get(compType);
          String compStr = new String(compType, java.nio.charset.StandardCharsets.US_ASCII);
          if (!"NONE".equals(compStr) && !"twos".equals(compStr) && !"able".equals(compStr)) {
            throw new IOException("Unsupported AIFF-C compression: " + compStr);
          }
          commExtra -= 4;
        }

        // Skip any remaining COMM bytes (name string in AIFC, etc.)
        for (int i = 0; i < commExtra; i++) bb.get();

      } else if (chunkId == 0x53534E44) { // "SSND"
        int offset = bb.getInt();
        int blockSize = bb.getInt();
        int dataSize = chunkSize - 8;
        if (dataSize < 0) throw new IOException("Invalid SSND chunk size: " + chunkSize);

        // Skip offset bytes
        if (offset > 0) {
          bb.position(bb.position() + offset);
          dataSize -= offset;
        }
        if (dataSize > 0) {
          pcmData = new byte[dataSize];
          bb.get(pcmData);
        }
      } else {
        bb.position(bb.position() + paddedSize);
      }
    }

    if (numChannels < 1 || sampleRate < 1 || bitsPerSample < 1 || pcmData == null) {
      throw new IOException("Incomplete AIFF: missing COMM or SSND chunk");
    }

    int bytesPerSample = (bitsPerSample + 7) / 8;
    int bytesPerFrame = numChannels * bytesPerSample;
    int frameCount = pcmData.length / bytesPerFrame;

    float[][] channels = new float[2][frameCount];
    for (int f = 0; f < frameCount; f++) {
      int frameOffset = f * bytesPerFrame;
      for (int c = 0; c < Math.min(numChannels, 2); c++) {
        channels[c][f] =
            readPcmSample(pcmData, frameOffset + c * bytesPerSample, bytesPerSample, bitsPerSample);
      }
      if (numChannels == 1) channels[1][f] = channels[0][f];
    }

    return new AiffData(channels, sampleRate, bitsPerSample);
  }

  private static void checkId(ByteBuffer bb, int a, int b, int c, int d) throws IOException {
    if (bb.get() != a || bb.get() != b || bb.get() != c || bb.get() != d)
      throw new IOException("Not an AIFF file (bad FORM/type ID)");
  }

  /**
   * Read AIFF's 80-bit SANE extended precision float → int (sample rate).
   *
   * <p>SANE Extended differs from IEEE 754 80-bit: there is NO implicit leading 1 bit. The 64-bit
   * mantissa is a pure unsigned fraction with the binary point at the left, so the value is:
   *
   * <pre>  value = 2^(exponent-16383) × mantissa / 2^64</pre>
   *
   * For typical sample rates (44100, 48000) the unbiased exponent is 15 or 16, so the integer part
   * fits in a few bits of the high mantissa word.
   */
  private static int readExtended80(ByteBuffer bb) {
    int exponent = bb.getShort() & 0xFFFF;
    long mantHigh = bb.getInt() & 0xFFFFFFFFL;
    long mantLow = bb.getInt() & 0xFFFFFFFFL;

    if (exponent == 0) return 0;
    // SANE extended: mantissa = mantissa / 2^64 (pure fraction, binary point at left)
    // The integer part lives in the high bits of mantHigh.
    // Unbiased exponent tells us how many bits of mantissa form the integer.
    int unbiased = exponent - 16383;
    if (unbiased < 0) return 0;
    if (unbiased > 31) return 0; // overflow safeguard — nothing we deal with

    // Build the complete 64-bit mantissa as a fraction:
    //   mantissa / 2^64  where mantissa = (mantHigh << 32) | mantLow
    // But unbiased tells us integer bits. unbiased = 16 means bits 63..48 are integer.
    // That's the top 16 bits of mantHigh.
    // Integer part = (mantHigh) >>> (32 - unbiased)
    return (int) (mantHigh >>> (32 - unbiased));
  }

  /** Fallback when sample rate can't be read from COMM — infer from frame count. */
  private static int guessSampleRate(long numSampleFrames) {
    // Very rough fallback — shouldn't normally be hit
    if (numSampleFrames > 0 && numSampleFrames < 100000) return 44100;
    return 44100;
  }

  private static float readPcmSample(
      byte[] data, int offset, int bytesPerSample, int bitsPerSample) {
    if (bitsPerSample == 8) {
      return (data[offset] & 0xFF) / 128.0f - 1.0f;
    }
    int raw = 0;
    for (int i = 0; i < bytesPerSample; i++) {
      raw = (raw << 8) | (data[offset + i] & 0xFF);
    }
    int signExtend = 32 - bitsPerSample;
    raw = (raw << signExtend) >> signExtend;
    return raw / (float) (1 << (bitsPerSample - 1));
  }
}
