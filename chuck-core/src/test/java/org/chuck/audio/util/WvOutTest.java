package org.chuck.audio.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class WvOutTest {

  @TempDir File tempDir;

  @Test
  void testMultiTrackStemRecording() throws IOException {
    int channels = 4;
    WvOut wvOut = new WvOut(44100.0f, channels);
    String baseName = new File(tempDir, "test_stem").getAbsolutePath();

    wvOut.openMultiTrack(baseName);
    assertTrue(wvOut.isRecording(), "WvOut should be in recording state");

    // Write 100 frames across 4 channels
    float[] frame = new float[channels];
    for (int i = 0; i < 100; i++) {
      frame[0] = 0.5f; // Ch0 constant 0.5
      frame[1] = -0.25f; // Ch1 constant -0.25
      frame[2] = 0.0f; // Ch2 zero
      frame[3] = 1.0f; // Ch3 full scale
      wvOut.recordFrame(frame, channels);
    }

    wvOut.close();
    assertFalse(wvOut.isRecording(), "WvOut should be closed");

    // Verify all 4 stem files exist and have exact expected header + data sizes
    // 44 bytes header + 100 samples * 1 channel * 2 bytes = 244 bytes per mono stem file
    for (int c = 0; c < channels; c++) {
      File stemFile = new File(baseName + "_ch" + c + ".wav");
      assertTrue(stemFile.exists(), "Stem file " + stemFile.getName() + " must exist");
      assertEquals(244, stemFile.length(), "Stem file length must be 44 (header) + 200 (data)");
    }
  }

  @Test
  void testInterleavedMultiChannelRecording() throws IOException {
    int channels = 6; // 5.1 surround
    WvOut wvOut = new WvOut(48000.0f, channels);
    File outFile = new File(tempDir, "surround_mix.wav");

    wvOut.open(outFile.getAbsolutePath());
    float[] frame = new float[channels];
    for (int i = 0; i < 50; i++) {
      wvOut.recordFrame(frame, channels);
    }
    wvOut.close();

    // 44 bytes header + 50 samples * 6 channels * 2 bytes = 644 bytes
    assertEquals(644, outFile.length(), "Interleaved surround file must match exact byte length");
  }
}
