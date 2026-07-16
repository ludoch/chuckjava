package org.chuck.audio.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.util.List;
import org.chuck.audio.ChuckAudio.DeviceInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Enumeration (fast, no PCM device needed — queries ALSA's config/plugin registry) and real-PCM
 * (tagged {@code slow}, only runs under {@code -Pslow-tests}) coverage for {@link AlsaBackend}.
 */
public class AlsaBackendTest {

  @Test
  public void testNameAndAvailability() {
    AlsaBackend backend = new AlsaBackend();
    assertEquals("ALSA", backend.name());
    // Must never throw, on any platform.
    boolean available = backend.isAvailable();
    System.out.println("[AlsaBackendTest] isAvailable=" + available);
  }

  @Test
  public void testDeviceEnumerationDoesNotThrow() {
    AlsaBackend backend = new AlsaBackend();
    assumeTrue(backend.isAvailable(), "ALSA not available on this platform");

    List<DeviceInfo> outputs = backend.getOutputDeviceInfo();
    List<DeviceInfo> inputs = backend.getInputDeviceInfo();
    assertNotNull(outputs);
    assertNotNull(inputs);

    System.out.println("[AlsaBackendTest] output devices: " + outputs.size());
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

    System.out.println("[AlsaBackendTest] input devices: " + inputs.size());
    for (DeviceInfo d : inputs) {
      assertTrue(d.maxInputChannels() > 0);
      assertFalse(d.nativeInputFormats().isEmpty());
    }
  }

  @Test
  @Tag("slow")
  public void testOpenStreamWriteAndClose() throws Exception {
    AlsaBackend backend = new AlsaBackend();
    assumeTrue(backend.isAvailable(), "ALSA not available on this platform");
    List<DeviceInfo> outputs = backend.getOutputDeviceInfo();
    assumeTrue(!outputs.isEmpty(), "no ALSA output device on this machine");
    // Prefer a direct plughw device over "default" - talking straight to the kernel driver
    // (bypassing PulseAudio/PipeWire) is the actual point of this backend.
    String deviceName =
        outputs.stream()
            .map(DeviceInfo::name)
            .filter(n -> n.startsWith("plughw:"))
            .findFirst()
            .orElse(outputs.get(0).name());

    AudioStreamConfig config =
        new AudioStreamConfig(
            deviceName,
            "",
            44100,
            2,
            0,
            512,
            4,
            org.chuck.audio.AudioSampleFormat.INT16,
            false,
            false);
    AudioBackendStream stream = backend.openStream(config);
    try {
      assertTrue(stream.getActualSampleRate() > 0);
      assertTrue(stream.getEffectiveBufferSize() > 0);

      stream.start();
      assertTrue(stream.isRunning());

      // A few buffers of a 440Hz sine wave, interleaved stereo.
      int frames = stream.getEffectiveBufferSize();
      float[] out = new float[frames * 2];
      double phaseInc = 2 * Math.PI * 440.0 / stream.getActualSampleRate();
      double phase = 0;
      for (int buf = 0; buf < 20; buf++) {
        for (int i = 0; i < frames; i++) {
          float sample = (float) (0.2 * Math.sin(phase));
          phase += phaseInc;
          out[i * 2] = sample;
          out[i * 2 + 1] = sample;
        }
        stream.writeOutput(out, 0, out.length);
      }

      System.out.println(
          "[AlsaBackendTest] rate="
              + stream.getActualSampleRate()
              + " effBuf="
              + stream.getEffectiveBufferSize()
              + " outputLatencySamples="
              + stream.getOutputLatencySamples()
              + " underruns="
              + stream.getUnderrunCount());

      stream.stop();
      assertFalse(stream.isRunning());
    } finally {
      stream.close();
    }
  }
}
