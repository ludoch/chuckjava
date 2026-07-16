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

  private FileOutputStream[] stemOutputs;

  public void open(String filename) throws IOException {
    close();
    fos = new FileOutputStream(filename);
    byte[] header = new byte[44];
    fos.write(header);
    totalSamples = 0;
  }

  public void openMultiTrack(String baseFilename) throws IOException {
    close();
    stemOutputs = new FileOutputStream[numChannels];
    for (int c = 0; c < numChannels; c++) {
      String stemPath = baseFilename + "_ch" + c + ".wav";
      stemOutputs[c] = new FileOutputStream(stemPath);
      stemOutputs[c].write(new byte[44]);
    }
    totalSamples = 0;
  }

  public void record(float left, float right) throws IOException {
    if (fos == null && stemOutputs == null) return;
    if (stemOutputs != null) {
      writeSampleToStream(stemOutputs[0], left);
      for (int c = 1; c < stemOutputs.length; c++) {
        writeSampleToStream(stemOutputs[c], c == 1 ? right : 0.0f);
      }
      totalSamples++;
      return;
    }
    writeSampleToStream(fos, left);
    if (numChannels > 1) {
      writeSampleToStream(fos, right);
      for (int c = 2; c < numChannels; c++) writeSampleToStream(fos, 0.0f);
    }
    totalSamples++;
  }

  public void recordFrame(float[] samples, int count) throws IOException {
    if (fos == null && stemOutputs == null) return;
    if (stemOutputs != null) {
      for (int c = 0; c < stemOutputs.length; c++) {
        float sample = (samples != null && c < count) ? samples[c] : 0.0f;
        writeSampleToStream(stemOutputs[c], sample);
      }
      totalSamples++;
      return;
    }
    for (int c = 0; c < numChannels; c++) {
      float sample = (samples != null && c < count) ? samples[c] : 0.0f;
      writeSampleToStream(fos, sample);
    }
    totalSamples++;
  }

  private void writeSampleToStream(FileOutputStream stream, float sample) throws IOException {
    if (stream == null) return;
    short pcm = (short) (Math.max(-1.0f, Math.min(1.0f, sample)) * 32767.0f);
    stream.write(pcm & 0xFF);
    stream.write((pcm >> 8) & 0xFF);
  }

  public void close() throws IOException {
    if (stemOutputs != null) {
      for (int c = 0; c < stemOutputs.length; c++) {
        if (stemOutputs[c] != null) {
          finalizeWavHeader(stemOutputs[c], 1, totalSamples, sampleRate);
          stemOutputs[c].close();
          stemOutputs[c] = null;
        }
      }
      stemOutputs = null;
    }
    if (fos != null) {
      finalizeWavHeader(fos, numChannels, totalSamples, sampleRate);
      fos.close();
      fos = null;
    }
  }

  private static void finalizeWavHeader(
      FileOutputStream stream, int channels, long samples, float sr) throws IOException {
    long byteRate = (long) sr * channels * 2;
    long dataSize = samples * channels * 2;
    long fileSize = 36 + dataSize;

    ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
    header.put("RIFF".getBytes());
    header.putInt((int) fileSize);
    header.put("WAVE".getBytes());
    header.put("fmt ".getBytes());
    header.putInt(16); // subchunk1size
    header.putShort((short) 1); // audio format (PCM)
    header.putShort((short) channels);
    header.putInt((int) sr);
    header.putInt((int) byteRate);
    header.putShort((short) (channels * 2)); // block align
    header.putShort((short) 16); // bits per sample
    header.put("data".getBytes());
    header.putInt((int) dataSize);

    try (java.nio.channels.FileChannel fc = stream.getChannel()) {
      fc.position(0);
      fc.write(header);
    }
  }

  public boolean isRecording() {
    return fos != null || stemOutputs != null;
  }
}
