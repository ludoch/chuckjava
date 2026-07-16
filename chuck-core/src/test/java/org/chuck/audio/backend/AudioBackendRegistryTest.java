package org.chuck.audio.backend;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class AudioBackendRegistryTest {

  @Test
  void testAvailableBackendsIncludesJavaSound() {
    List<AudioBackend> backends = AudioBackendRegistry.getAvailableBackends();
    assertNotNull(backends, "Available backends list should not be null");
    assertFalse(backends.isEmpty(), "At least one backend must be available");

    boolean hasJavaSound = backends.stream().anyMatch(b -> b.name().equals("JavaSound"));
    assertTrue(hasJavaSound, "JavaSoundBackend must always be registered and available");
  }

  @Test
  void testGetDefaultBackendAlwaysReturnsValidBackend() {
    AudioBackend defaultBackend = AudioBackendRegistry.getDefaultBackend();
    assertNotNull(defaultBackend, "Default backend must not be null");
    assertTrue(defaultBackend.isAvailable(), "Default backend must be available");
    assertNotNull(defaultBackend.name(), "Default backend name must not be null");
  }

  @Test
  void testGetBackendByNameMatchesCaseInsensitive() {
    AudioBackend js = AudioBackendRegistry.getBackendByName("javasound");
    assertNotNull(js, "Should resolve javasound case-insensitively");
    assertEquals("JavaSound", js.name());

    AudioBackend nonExistent = AudioBackendRegistry.getBackendByName("NonExistentDriver_XYZ");
    assertNull(nonExistent, "Should return null for non-existent driver");
  }

  @Test
  void testLowLatencyConfigOpenStream() throws Exception {
    AudioBackend backend = AudioBackendRegistry.getDefaultBackend();
    AudioStreamConfig config =
        new AudioStreamConfig(
            null, null, 44100, 2, 0, 256, 4, org.chuck.audio.AudioSampleFormat.FLOAT32, true, true);
    try (AudioBackendStream stream = backend.openStream(config)) {
      assertNotNull(stream, "Stream should open cleanly");
      assertEquals(44100, stream.getActualSampleRate(), "Sample rate must match requested");
      assertTrue(stream.getEffectiveBufferSize() > 0, "Buffer size must be positive");
      assertFalse(stream.isRunning(), "Stream should not be running before start()");
    }
  }
}
