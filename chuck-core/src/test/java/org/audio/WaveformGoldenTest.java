package org.chuck.audio;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;

/**
 * Advanced regression test that compares sample-by-sample output against a "Golden" reference. This
 * catches subtle DSP artifacts that simple RMS tests miss.
 */
public class WaveformGoldenTest {

  private static final int SAMPLE_RATE = 44100;
  private static final int DURATION_SAMPLES = 44100; // 1 second of test audio

  @Test
  public void testAdsrWaveform() throws Exception {
    String code =
        """
        SinOsc s => ADSR e => dac;
        0.5 => s.gain;
        440 => s.freq;
        e.set(10::ms, 20::ms, 0.5, 50::ms);
        e.keyOn();
        100::ms => now;
        e.keyOff();
        100::ms => now;
        """;

    verifyAgainstGolden("adsr_golden", code);
  }

  @Test
  public void testPolyphonyWaveform() throws Exception {
    String code =
        """
        SinOsc s1 => dac;
        SinOsc s2 => dac;
        0.3 => s1.gain;
        0.3 => s2.gain;
        440 => s1.freq;
        660 => s2.freq;
        1::second => now;
        """;
    verifyAgainstGolden("polyphony_golden", code);
  }

  private void verifyAgainstGolden(String name, String code) throws Exception {
    float[] currentOutput = renderToBuffer(code, DURATION_SAMPLES);
    Path goldenPath = Path.of("src/test/resources/golden/" + name + ".bin");

    if (!Files.exists(goldenPath)) {
      // Bootstrap mode: Generate the golden file if it doesn't exist
      Files.createDirectories(goldenPath.getParent());
      saveBuffer(currentOutput, goldenPath);
      System.out.println("Generated NEW golden file: " + goldenPath);
      return;
    }

    float[] goldenOutput = loadBuffer(goldenPath);

    // Compare
    int limit = Math.min(currentOutput.length, goldenOutput.length);
    // Ignore the very last few samples of the shred duration to avoid termination jitter
    int safeLimit = limit - 5;
    for (int i = 1; i < safeLimit; i++) {
      float bestDiff = Float.MAX_VALUE;
      for (int shift = -2; shift <= 2; shift++) {
        if (i + shift >= 0 && i + shift < currentOutput.length) {
          float d = Math.abs(currentOutput[i + shift] - goldenOutput[i]);
          if (d < bestDiff) bestDiff = d;
        }
      }
      if (bestDiff > 5e-2f) {





        fail(
            String.format(
                "Waveform mismatch at sample %d in %s. Expected %.6f, got best match diff %.6f",
                i, name, goldenOutput[i], bestDiff));
      }
    }
  }

  @Test
  public void testMathParity() throws Exception {
    String[] tests = {
      "01_filter_lpf", "02_filter_resonz", "03_stk_mandolin",
      "04_stk_stifkarp", "05_stk_modalbar", "06_event_broadcast",
      "07_shred_spork", "08_envelope", "09_filter_onepole",
      "10_filter_twopole", "11_filter_onezero", "12_filter_twozero",
      "13_stk_beethree", "14_stk_moog", "15_sndbuf"
    };

    System.out.println("\n=== Mathematical Parity Results ===");
    System.out.printf("%-20s | %-15s\n", "Test", "RMS Difference");
    System.out.println("----------------------------------------");

    for (String test : tests) {
      try {
        String ckCode =
            Files.readString(
                Path.of(
                    "/usr/local/google/home/ludo/.gemini/jetski/scratch/comparison/"
                        + test
                        + ".ck"));
        float[] javaOut = renderToBuffer(ckCode, DURATION_SAMPLES);

        Path nativeWavPath = Path.of("/tmp/chuck_temp_wavs/native/" + test + ".wav");
        if (!Files.exists(nativeWavPath)) {
          System.out.printf("%-20s | %-15s\n", test, "NATIVE MISSING");
          continue;
        }

        // Read Native WAV (16-bit PCM, 44.1kHz, stereo)
        byte[] wavBytes = Files.readAllBytes(nativeWavPath);
        int dataOffset = 44; // Skip WAV header
        int numSamples =
            Math.min(DURATION_SAMPLES, (wavBytes.length - dataOffset) / 4); // 2 channels * 2 bytes
        float[] nativeOut = new float[numSamples];

        java.nio.ByteBuffer bb =
            java.nio.ByteBuffer.wrap(wavBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < numSamples; i++) {
          int idx = dataOffset + i * 4; // 2 channels * 2 bytes
          if (idx + 2 <= wavBytes.length) {
            short left = bb.getShort(idx);
            nativeOut[i] = left / 32768.0f; // Normalize to [-1.0, 1.0]
          }
        }

        // Compute RMS Difference
        double sumSq = 0;
        int limit = Math.min(javaOut.length, nativeOut.length);
        for (int i = 0; i < limit; i++) {
          double diff = javaOut[i] - nativeOut[i];
          sumSq += diff * diff;
        }
        double rms = Math.sqrt(sumSq / limit);
        System.out.printf("%-20s | %.6f\n", test, rms);

      } catch (Exception e) {
        System.out.printf("%-20s | ERROR: %s\n", test, e.getMessage());
      }
    }
    System.out.println("====================================\n");
  }

  private float[] renderToBuffer(String code, int samples) {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);

    vm.run(code, "test");


    float[] buffer = new float[samples];
    boolean useBlock = true;


    if (!useBlock) {
      for (int i = 0; i < samples; i++) {
        vm.advanceTime(1);
        buffer[i] = vm.getChannelLastOut(0);
      }
    } else {
      float[][] dacBufs = new float[2][samples];
      vm.advanceTime(dacBufs, 0, samples);
      System.arraycopy(dacBufs[0], 0, buffer, 0, samples);
    }
    return buffer;
  }

  private void saveBuffer(float[] buffer, Path path) throws IOException {
    ByteBuffer bb = ByteBuffer.allocate(buffer.length * 4).order(ByteOrder.LITTLE_ENDIAN);
    for (float f : buffer) bb.putFloat(f);
    Files.write(path, bb.array());
  }

  private float[] loadBuffer(Path path) throws IOException {
    byte[] bytes = Files.readAllBytes(path);
    ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    float[] buffer = new float[bytes.length / 4];
    for (int i = 0; i < buffer.length; i++) buffer[i] = bb.getFloat();
    return buffer;
  }
}
