package org.chuck.audio.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.chuck.audio.ChuckUGen;

/** WvOut2: Stereo recording UGen. */
public class WvOut2 extends StereoUGen {
  private FileOutputStream fos;
  private long totalSamples = 0;
  private float sampleRate = 44100.0f;

  public WvOut2(float sampleRate) {
    super();
    this.sampleRate = sampleRate;
  }

  @Override
  protected void computeStereo(float input, long systemTime) {
    // PRC: computeStereo is called AFTER inputs are summed into 'input'
    // But for WvOut2 we often want the inputs to remain separate if they were stereo.
    // StereoUGen.tick sums all sources into 'input'.
    // If we want real stereo recording of stereo sources, we should look at the sources.

    float left = 0, right = 0;
    for (ChuckUGen src : getSources()) {
      // Sources are already ticked by StereoUGen.tick
      left += src.getChannelLastOut(0);
      right += src.getChannelLastOut(1);
    }

    lastOutChannels[0] = left;
    lastOutChannels[1] = right;

    if (fos != null) {
      try {
        writeSample(left);
        writeSample(right);
        totalSamples++;
      } catch (IOException e) {
        closeFile();
      }
    }
  }

  public void wavWrite(String filename) {
    try {
      openFile(filename);
    } catch (IOException e) {
    }
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

  private void openFile(String filename) throws IOException {
    if (fos != null) closeFile();
    fos = new FileOutputStream(filename);
    byte[] header = new byte[44];
    fos.write(header);
    totalSamples = 0;
  }

  private void writeSample(float sample) throws IOException {
    short pcm = (short) (Math.max(-1.0f, Math.min(1.0f, sample)) * 32767.0f);
    fos.write(pcm & 0xFF);
    fos.write((pcm >> 8) & 0xFF);
  }

  private void finalizeWav() throws IOException {
    if (fos == null) return;
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
    header.putShort((short) 2); // 2 channels
    header.putInt((int) sampleRate);
    header.putInt((int) byteRate);
    header.putShort((short) 4); // block align
    header.putShort((short) 16);
    header.put("data".getBytes());
    header.putInt((int) dataSize);

    try (java.nio.channels.FileChannel fc = fos.getChannel()) {
      fc.position(0);
      fc.write(header);
    }
  }
}
