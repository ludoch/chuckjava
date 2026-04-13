package org.chuck.audio;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.*;
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
        200::ms => now;
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
    for (int i = 0; i < safeLimit; i++) {
      float diff = Math.abs(currentOutput[i] - goldenOutput[i]);
      if (diff > 5e-5f) {
        fail(
            String.format(
                "Waveform mismatch at sample %d in %s. Expected %.6f, got %.6f (diff=%.6f)",
                i, name, goldenOutput[i], currentOutput[i], diff));
      }
    }
  }

  private float[] renderToBuffer(String code, int samples) {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.run(code, "test");

    float[] buffer = new float[samples];
    // Use scalar mode for stable, bit-exact verification
    for (int i = 0; i < samples; i++) {
      vm.advanceTime(1);
      buffer[i] = vm.getChannelLastOut(0);
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
