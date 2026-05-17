package org.chuck.audio.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility to read WAV (RIFF) files using pure java.io — no {@code javax.sound} dependency.
 *
 * <p>Reads 8/16/24-bit PCM and 32-bit IEEE float mono/stereo WAV files and converts to normalized
 * float arrays ({@code float[][]} where index 0 = left, index 1 = right, mono duplicates to both
 * channels).
 */
public class WavReader {

  /** Result of a WAV read operation. */
  public static class WavData {
    public final float[][] channels; // [channel][sample], always 2 with mono duplicated
    public final int sampleRate;
    public final int bitsPerSample;

    public WavData(float[][] channels, int sampleRate, int bitsPerSample) {
      this.channels = channels;
      this.sampleRate = sampleRate;
      this.bitsPerSample = bitsPerSample;
    }

    /** Total number of frames (samples per channel). */
    public int frameCount() {
      return channels[0].length;
    }
  }

  /**
   * Read a WAV file from a {@link File}.
   *
   * @throws IOException if the file cannot be read or is not a valid WAV
   */
  public static WavData read(File file) throws IOException {
    long length = file.length();
    if (length > Integer.MAX_VALUE) {
      throw new IOException("File too large: " + file.length());
    }
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

  /**
   * Read a WAV file from an {@link InputStream}. Reads the entire stream into a byte array, then
   * parses it.
   */
  public static WavData read(InputStream stream) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    while (true) {
      int read = stream.read(buf);
      if (read < 0) break;
      baos.write(buf, 0, read);
    }
    return read(baos.toByteArray());
  }

  /**
   * Read a WAV file from an in-memory byte array containing the complete RIFF/WAV data.
   *
   * @throws IOException if the data is not a valid WAV
   */
  public static WavData read(byte[] data) throws IOException {
    ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

    // RIFF header
    if (bb.get() != 'R' || bb.get() != 'I' || bb.get() != 'F' || bb.get() != 'F') {
      throw new IOException("Not a RIFF file");
    }
    bb.getInt(); // file size (skip)
    if (bb.get() != 'W' || bb.get() != 'A' || bb.get() != 'V' || bb.get() != 'E') {
      throw new IOException("Not a WAVE file");
    }

    int numChannels = -1;
    int sampleRate = -1;
    int bitsPerSample = -1;
    int formatTag = -1;
    byte[] pcmData = null;

    // Scan chunks
    while (bb.remaining() >= 8) {
      int chunkId = bb.getInt();
      int chunkSize = bb.getInt();
      if (chunkSize < 0 || bb.remaining() < chunkSize) {
        break; // truncated or corrupt
      }

      if (chunkId == 0x20746D66) { // "fmt " (little-endian int)
        if (chunkSize < 16) {
          throw new IOException("Format chunk too small: " + chunkSize);
        }
        formatTag = bb.getShort() & 0xFFFF;
        if (formatTag != 1
            && formatTag != 3
            && formatTag != 0xFFFE) { // PCM, IEEE float, or extensible
          throw new IOException(
              "Unsupported WAV format: " + formatTag + " (only PCM/IEEE float supported)");
        }
        numChannels = bb.getShort() & 0xFFFF;
        sampleRate = bb.getInt();
        bb.getInt(); // byte rate (skip)
        bb.getShort(); // block align (skip)
        bitsPerSample = bb.getShort() & 0xFFFF;

        // Skip remaining fmt chunk bytes
        int fmtRemaining = chunkSize - 16;
        for (int i = 0; i < fmtRemaining; i++) bb.get();
      } else if (chunkId == 0x61746164) { // "data"
        pcmData = new byte[chunkSize];
        bb.get(pcmData);
      } else {
        // Skip other chunks (fact, cue, smpl, etc.)
        bb.position(bb.position() + chunkSize);
      }
    }

    if (numChannels < 1 || sampleRate < 1 || bitsPerSample < 1 || pcmData == null) {
      throw new IOException("Incomplete WAV: missing fmt or data chunk");
    }

    // Convert PCM bytes to float samples
    int bytesPerSample = bitsPerSample / 8;
    int bytesPerFrame = numChannels * bytesPerSample;
    int frameCount = pcmData.length / bytesPerFrame;

    float[][] channels = new float[2][frameCount];

    boolean isFloat = (formatTag == 3);

    for (int f = 0; f < frameCount; f++) {
      int frameOffset = f * bytesPerFrame;

      for (int c = 0; c < Math.min(numChannels, 2); c++) {
        int sampleOffset = frameOffset + c * bytesPerSample;
        if (isFloat) {
          channels[c][f] = readFloatSample(pcmData, sampleOffset);
        } else {
          channels[c][f] = readPcmSample(pcmData, sampleOffset, bytesPerSample, bitsPerSample);
        }
      }

      // If mono, duplicate to both channels
      if (numChannels == 1) {
        channels[1][f] = channels[0][f];
      }
    }

    return new WavData(channels, sampleRate, bitsPerSample);
  }

  /**
   * Read a single PCM sample from a byte array at the given offset and convert to float in [-1, 1].
   */
  private static float readPcmSample(
      byte[] data, int offset, int bytesPerSample, int bitsPerSample) {
    if (bitsPerSample == 8) {
      // Unsigned 8-bit
      return (data[offset] & 0xFF) / 128.0f - 1.0f;
    }

    // Signed little-endian integer
    int raw = 0;
    for (int i = bytesPerSample - 1; i >= 0; i--) {
      raw = (raw << 8) | (data[offset + i] & 0xFF);
    }
    // Sign extend
    int signExtend = 32 - bitsPerSample;
    raw = (raw << signExtend) >> signExtend;

    return raw / (float) (1 << (bitsPerSample - 1));
  }

  /**
   * Read a single 32-bit IEEE float sample from a byte array at the given offset. The float value
   * is already in [-1, 1] range per WAV specification.
   */
  private static float readFloatSample(byte[] data, int offset) {
    int bits =
        (data[offset + 3] & 0xFF) << 24
            | (data[offset + 2] & 0xFF) << 16
            | (data[offset + 1] & 0xFF) << 8
            | (data[offset] & 0xFF);
    return Float.intBitsToFloat(bits);
  }

  /**
   * Convenience: read a WAV file and return a mono float array (mixed down if stereo).
   *
   * @throws IOException on parse failure
   */
  public static float[] readMonoFloats(File file) throws IOException {
    WavData data = read(file);
    float[] mono = new float[data.frameCount()];
    for (int i = 0; i < mono.length; i++) {
      mono[i] = (data.channels[0][i] + data.channels[1][i]) * 0.5f;
    }
    return mono;
  }

  /**
   * Convenience: read a WAV file and return stereo interleaved float array [L,R,L,R,...].
   *
   * @throws IOException on parse failure
   */
  public static float[] readStereoFloats(File file) throws IOException {
    WavData data = read(file);
    float[] interleaved = new float[data.frameCount() * 2];
    for (int i = 0; i < data.frameCount(); i++) {
      interleaved[i * 2] = data.channels[0][i];
      interleaved[i * 2 + 1] = data.channels[1][i];
    }
    return interleaved;
  }
}
