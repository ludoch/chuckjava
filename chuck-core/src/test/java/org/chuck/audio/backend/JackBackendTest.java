package org.chuck.audio.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.util.List;
import org.chuck.audio.ChuckAudio.DeviceInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Enumeration (fast) and real-server (tagged {@code slow}, only runs under {@code -Pslow-tests})
 * coverage for {@link JackBackend}. The real-server test requires a running {@code jackd} — starts
 * against whatever server is already running (e.g. a {@code jackd -d dummy} instance for CI/sandbox
 * use, no physical audio hardware required since JACK's dummy backend still drives the real process
 * callback graph on its own timer).
 */
public class JackBackendTest {

  @Test
  public void testNameAndAvailability() {
    JackBackend backend = new JackBackend();
    assertEquals("JACK", backend.name());
    boolean available = backend.isAvailable();
    System.out.println("[JackBackendTest] isAvailable=" + available);
  }

  @Test
  public void testDeviceEnumerationDoesNotThrow() {
    JackBackend backend = new JackBackend();
    assumeTrue(backend.isAvailable(), "JACK not available on this platform");

    List<DeviceInfo> outputs = backend.getOutputDeviceInfo();
    List<DeviceInfo> inputs = backend.getInputDeviceInfo();
    assertNotNull(outputs);
    assertNotNull(inputs);
    for (DeviceInfo d : outputs) {
      assertTrue(d.maxOutputChannels() > 0);
      assertEquals(
          0, d.maxInputChannels(), "an output-device entry should not claim input channels");
    }
    for (DeviceInfo d : inputs) {
      assertTrue(d.maxInputChannels() > 0);
      assertEquals(
          0, d.maxOutputChannels(), "an input-device entry should not claim output channels");
    }
  }

  @Test
  @Tag("slow")
  public void testOpenStreamWriteAndClose() throws Exception {
    JackBackend backend = new JackBackend();
    assumeTrue(backend.isAvailable(), "JACK not available on this platform");

    AudioStreamConfig config =
        new AudioStreamConfig(
            "", "", 44100, 2, 0, 512, 4, org.chuck.audio.AudioSampleFormat.FLOAT32, false, false);
    AudioBackendStream stream;
    try {
      stream = backend.openStream(config);
    } catch (Exception e) {
      // No jackd server reachable in this environment - same graceful-skip contract as ALSA
      // when no PCM device exists.
      assumeTrue(
          false, "could not open a JACK stream (no jackd server running?): " + e.getMessage());
      return;
    }

    try {
      assertTrue(stream.getActualSampleRate() > 0);
      assertTrue(stream.getEffectiveBufferSize() > 0);
      System.out.println(
          "[JackBackendTest] server rate="
              + stream.getActualSampleRate()
              + " effBuf="
              + stream.getEffectiveBufferSize());

      stream.start();
      assertTrue(stream.isRunning());
      // Give jack_activate + auto-connect a moment to settle before the process callback starts
      // consuming what we write.
      Thread.sleep(100);

      int frames = stream.getEffectiveBufferSize();
      float[] out = new float[frames * 2];
      double phaseInc = 2 * Math.PI * 440.0 / stream.getActualSampleRate();
      double phase = 0;
      // Write faster than real-time briefly, then pace writes to roughly match the server's
      // callback cadence so the ring buffer neither starves nor overflows for this short test.
      for (int buf = 0; buf < 40; buf++) {
        for (int i = 0; i < frames; i++) {
          float sample = (float) (0.2 * Math.sin(phase));
          phase += phaseInc;
          out[i * 2] = sample;
          out[i * 2 + 1] = sample;
        }
        stream.writeOutput(out, 0, out.length);
        Thread.sleep(Math.max(1, (long) (frames * 1000.0 / stream.getActualSampleRate())));
      }

      System.out.println(
          "[JackBackendTest] underruns="
              + stream.getUnderrunCount()
              + " overflows="
              + stream.getOverflowCount());

      stream.stop();
      assertFalse(stream.isRunning());
    } finally {
      stream.close();
    }
  }
}
