package org.chuck.audio.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.util.List;
import org.chuck.audio.ChuckAudio.DeviceInfo;
import org.junit.jupiter.api.Test;

/**
 * Enumeration and real-PCM stream verification coverage for {@link CoreAudioBackend} on macOS
 * (Project Panama FFM). Self-skips via {@code assumeTrue(backend.isAvailable())} on non-macOS
 * platforms.
 */
public class CoreAudioBackendTest {

  @Test
  public void testNameAndAvailability() {
    CoreAudioBackend backend = new CoreAudioBackend();
    assertEquals("CoreAudio", backend.name());
    boolean available = backend.isAvailable();
    System.out.println("[CoreAudioBackendTest] isAvailable=" + available);
    if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
      assertTrue(available, "CoreAudioBackend should be available when running on macOS");
    }
  }

  @Test
  public void testDeviceEnumerationDoesNotThrow() {
    CoreAudioBackend backend = new CoreAudioBackend();
    assumeTrue(backend.isAvailable(), "CoreAudio not available on this platform");

    List<DeviceInfo> outputs = backend.getOutputDeviceInfo();
    List<DeviceInfo> inputs = backend.getInputDeviceInfo();
    assertNotNull(outputs);
    assertNotNull(inputs);

    System.out.println("[CoreAudioBackendTest] output devices: " + outputs.size());
    for (DeviceInfo d : outputs) {
      System.out.println(
          "  "
              + d.name()
              + " maxOut="
              + d.maxOutputChannels()
              + " preferredRate="
              + d.preferredSampleRate()
              + " rates="
              + d.supportedSampleRates()
              + " formats="
              + d.nativeOutputFormats());
      assertTrue(d.maxOutputChannels() > 0);
      assertFalse(d.nativeOutputFormats().isEmpty());
    }
  }

  @Test
  public void testOpenStreamWriteAndCloseOnMac() throws Exception {
    CoreAudioBackend backend = new CoreAudioBackend();
    assumeTrue(backend.isAvailable(), "CoreAudio not available on this platform");
    List<DeviceInfo> outputs = backend.getOutputDeviceInfo();
    assumeTrue(!outputs.isEmpty(), "no CoreAudio output device on this machine");

    AudioStreamConfig config =
        new AudioStreamConfig(
            outputs.get(0).name(),
            "",
            44100,
            2,
            0,
            512,
            4,
            org.chuck.audio.AudioSampleFormat.FLOAT32,
            true,
            false);
    AudioBackendStream stream = backend.openStream(config);
    try {
      assertTrue(stream.getActualSampleRate() > 0);
      assertTrue(stream.getEffectiveBufferSize() > 0);

      stream.start();
      assertTrue(stream.isRunning(), "Stream should be running after start()");

      // Write 50 buffers of a 440Hz sine wave (interleaved stereo float32)
      int frames = stream.getEffectiveBufferSize();
      float[] out = new float[frames * 2];
      double phaseInc = 2 * Math.PI * 440.0 / stream.getActualSampleRate();
      double phase = 0;
      for (int buf = 0; buf < 50; buf++) {
        for (int i = 0; i < frames; i++) {
          float sample = (float) (0.2 * Math.sin(phase));
          phase += phaseInc;
          out[i * 2] = sample;
          out[i * 2 + 1] = sample;
        }
        stream.writeOutput(out, 0, out.length);
        Thread.sleep(10);
      }

      System.out.println(
          "[CoreAudioBackendTest] rate="
              + stream.getActualSampleRate()
              + " effBuf="
              + stream.getEffectiveBufferSize()
              + " outputLatencySamples="
              + stream.getOutputLatencySamples()
              + " underruns="
              + stream.getUnderrunCount());

      stream.stop();
      assertFalse(stream.isRunning(), "Stream should not be running after stop()");
    } finally {
      stream.close();
    }
  }
}
