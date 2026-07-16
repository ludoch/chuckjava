package org.chuck.audio.backend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registry and discovery service for available {@link AudioBackend} drivers in ChucK-Java.
 *
 * <p>Probes native FFM low-latency backends ({@link CoreAudioBackend}, {@link WASAPIBackend}, and
 * {@link JackBackend}) alongside the cross-platform {@link JavaSoundBackend}, and selects the
 * optimal driver based on system properties ({@code -Dchuck.audio.backend=<name>}) and host OS.
 */
public class AudioBackendRegistry {
  private static final Logger logger = Logger.getLogger(AudioBackendRegistry.class.getName());
  private static final List<AudioBackend> registeredBackends = new ArrayList<>();

  static {
    // Probe backends in priority order: FFM Low-Latency first, JavaSound last as guaranteed
    // fallback
    registerBackendSafe(new CoreAudioBackend());
    registerBackendSafe(new WASAPIBackend());
    registerBackendSafe(new JackBackend());
    registerBackendSafe(new JavaSoundBackend());
  }

  private static void registerBackendSafe(AudioBackend backend) {
    try {
      if (backend.isAvailable()) {
        registeredBackends.add(backend);
        logger.log(
            Level.FINE, "[AudioBackendRegistry] Registered active backend: " + backend.name());
      }
    } catch (Throwable t) {
      logger.log(
          Level.FINE,
          "[AudioBackendRegistry] Backend " + backend.name() + " unavailable: " + t.getMessage());
    }
  }

  /**
   * Returns all available audio backends on the current host system.
   *
   * @return unmodifiable list of active backends
   */
  public static List<AudioBackend> getAvailableBackends() {
    return Collections.unmodifiableList(registeredBackends);
  }

  /**
   * Resolves a backend by exact name (case-insensitive).
   *
   * @param name the backend name (e.g. "WASAPI", "CoreAudio", "JACK", "JavaSound")
   * @return the matching {@link AudioBackend}, or {@code null} if not found or unavailable
   */
  public static AudioBackend getBackendByName(String name) {
    if (name == null || name.isBlank()) return null;
    for (AudioBackend b : registeredBackends) {
      if (b.name().equalsIgnoreCase(name.trim())) {
        return b;
      }
    }
    return null;
  }

  /**
   * Selects and returns the default (optimal) audio backend for the current environment.
   *
   * <p>If {@code -Dchuck.audio.backend=<name>} is set, returns the requested backend (if
   * available). Otherwise, prioritizes native FFM low-latency drivers (`CoreAudio` on macOS,
   * `WASAPI` on Windows, `JACK` on Linux/Unix), and falls back seamlessly to `JavaSound` when FFM
   * is disabled or unavailable.
   *
   * @return the active default {@link AudioBackend}
   */
  public static AudioBackend getDefaultBackend() {
    String requested = System.getProperty("chuck.audio.backend");
    if (requested != null && !requested.isBlank()) {
      AudioBackend found = getBackendByName(requested);
      if (found != null) {
        logger.log(
            Level.INFO,
            "[AudioBackendRegistry] Using explicitly requested audio backend: " + found.name());
        return found;
      }
      logger.log(
          Level.WARNING,
          "[AudioBackendRegistry] Requested backend '"
              + requested
              + "' not available. Selecting optimal host fallback...");
    }

    // Return the first available backend (since registeredBackends is prioritized: CoreAudio ->
    // WASAPI -> JACK -> JavaSound)
    if (!registeredBackends.isEmpty()) {
      AudioBackend best = registeredBackends.get(0);
      if (!best.name().equals("JavaSound")) {
        logger.log(
            Level.INFO,
            "[AudioBackendRegistry] Auto-selected low-latency FFM audio backend: " + best.name());
      }
      return best;
    }

    // Should never happen since JavaSoundBackend.isAvailable() always returns true
    return new JavaSoundBackend();
  }
}
