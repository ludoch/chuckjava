package org.chuck.audio.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.chuck.core.ChuckShred;

/** WvOut2: Stereo recording UGen. Matches native ChucK. */
public class WvOut2 extends StereoUGen implements AutoCloseable {
  private FileOutputStream fos;
  private long totalSamples = 0;
  private float sampleRate = 44100.0f;
  private String currentFilename = "";
  private boolean recording = true;
  private float fileGain = 1.0f;
  private boolean registered = false;

  public WvOut2(float sampleRate) {
    super();
    this.sampleRate = sampleRate;
  }

  @Override
  public void addSource(org.chuck.audio.ChuckUGen src) {
    super.addSource(src);
    if (src != null) {
      src.cacheEnabled = false;
    }
  }


  @Override
  protected void computeStereo(float left, float right, long systemTime) {
    lastOutChannels[0] = left;
    lastOutChannels[1] = right;

    System.out.println("[WvOut2] computeStereo called: " + left + ", " + right);


    if (fos != null && recording) {
      try {
        writeSample(left * fileGain);
        writeSample(right * fileGain);
        totalSamples++;
      } catch (IOException e) {
        closeFile();
      }
    }
  }

  @Override
  protected void computeStereo(float input, long systemTime) {
    computeStereo(input, input, systemTime);
  }

  public String wavFilename(String filename) {
    try {
      openFile(filename);
    } catch (java.io.IOException e) {
      System.err.println("[WvOut2] Error opening file: " + e.getMessage());
    }

    return filename;
  }

  public float fileGain(float g) {
    this.fileGain = g;
    return g;
  }

  public int record(int status) {
    this.recording = (status != 0);
    return status;
  }

  public String filename() {
    return currentFilename;
  }

  public void wavWrite(String filename) {
    wavFilename(filename);
  }

  public void closeFile() {
    try {
      if (fos != null) {
        finalizeWav();
        fos.close();
        fos = null;
      }
    } catch (IOException e) {
    }
  }

  @Override
  public void close() throws Exception {
    closeFile();
  }

  private void openFile(String filename) throws IOException {
    if (fos != null) closeFile();
    this.currentFilename = filename;
    fos = new FileOutputStream(filename);
    byte[] header = new byte[44];
    fos.write(header);
    totalSamples = 0;

    if (!registered && ChuckShred.CURRENT_SHRED.isBound()) {
      ChuckShred.CURRENT_SHRED.get().registerCloseable(this);
      registered = true;
    }
  }

  private void writeSample(float sample) throws IOException {
    short pcm = (short) (Math.max(-1.0f, Math.min(1.0f, sample)) * 32767.0f);
    fos.write(pcm & 0xFF);
    fos.write((pcm >> 8) & 0xFF);
  }

  private void finalizeWav() throws IOException {
    if (fos == null) return;
    System.out.println("[WvOut2] Finalizing WAV: " + currentFilename + " samples: " + totalSamples);

    fos.flush();
    try {
      fos.getFD().sync();
    } catch (IOException ignored) {
    }

    long byteRate = (long) sampleRate * 2 * 2;
    long dataSize = totalSamples * 2 * 2;
    long fileSize = 36 + dataSize;

    ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
    header.put("RIFF".getBytes());
    header.putInt((int) fileSize);
    header.put("WAVE".getBytes());
    header.put("fmt ".getBytes());
    header.putInt(16);
    header.putShort((short) 1);
    header.putShort((short) 2);
    header.putInt((int) sampleRate);
    header.putInt((int) byteRate);
    header.putShort((short) 4);
    header.putShort((short) 16);
    header.put("data".getBytes());
    header.putInt((int) dataSize);

    try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(currentFilename, "rw")) {
      raf.seek(0);
      raf.write(header.array());
    }
  }
}
