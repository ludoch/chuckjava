package org.chuck.audio.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** A utility to record audio to a WAV file. */
public class WvOut {
  private FileOutputStream fos;
  private long totalSamples = 0;
  private final float sampleRate;
  private final int numChannels;

  public WvOut(float sampleRate, int numChannels) {
    this.sampleRate = sampleRate;
    this.numChannels = numChannels;
  }

  public void open(String filename) throws IOException {
    fos = new FileOutputStream(filename);
    // Write placeholder for WAV header
    byte[] header = new byte[44];
    fos.write(header);
    totalSamples = 0;
  }

  public void record(float left, float right) throws IOException {
    if (fos == null) return;

    writeSample(left);
    if (numChannels > 1) {
      writeSample(right);
    }
    totalSamples++;
  }

  private void writeSample(float sample) throws IOException {
    short pcm = (short) (Math.max(-1.0f, Math.min(1.0f, sample)) * 32767.0f);
    fos.write(pcm & 0xFF);
    fos.write((pcm >> 8) & 0xFF);
  }

  public void close() throws IOException {
    if (fos == null) return;

    // Finalize WAV header
    long byteRate = (long) sampleRate * numChannels * 2;
    long dataSize = totalSamples * numChannels * 2;
    long fileSize = 36 + dataSize;

    ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
    header.put("RIFF".getBytes());
    header.putInt((int) fileSize);
    header.put("WAVE".getBytes());
    header.put("fmt ".getBytes());
    header.putInt(16); // subchunk1size
    header.putShort((short) 1); // audio format (PCM)
    header.putShort((short) numChannels);
    header.putInt((int) sampleRate);
    header.putInt((int) byteRate);
    header.putShort((short) (numChannels * 2)); // block align
    header.putShort((short) 16); // bits per sample
    header.put("data".getBytes());
    header.putInt((int) dataSize);

    try (java.nio.channels.FileChannel fc = fos.getChannel()) {
      fc.position(0);
      fc.write(header);
    }

    fos.close();
    fos = null;
  }

  public boolean isRecording() {
    return fos != null;
  }
}
