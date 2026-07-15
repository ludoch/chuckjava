package org.chuck.audio.backend;

import java.util.List;
import org.chuck.audio.ChuckAudio.DeviceInfo;

/**
 * Pluggable audio driver backend for ChucK-Java.
 *
 * <p>Enables swappable low-latency drivers (JavaSound, WASAPI via FFM, JACK, ALSA) without
 * modifying core VM or UGen graph processing.
 */
public interface AudioBackend {
  /** The unique identifier of this audio backend (e.g., "JavaSound", "WASAPI", "JACK"). */
  String name();

  /** Whether this backend is supported and available on the current operating system. */
  boolean isAvailable();

  /** Enumerates output devices via this backend. */
  List<DeviceInfo> getOutputDeviceInfo();

  /** Enumerates input devices via this backend. */
  List<DeviceInfo> getInputDeviceInfo();

  /**
   * Opens an audio stream with the requested configuration.
   *
   * @param config the requested stream parameters
   * @return an open {@link AudioBackendStream} ready for start()
   * @throws Exception if the stream could not be opened
   */
  AudioBackendStream openStream(AudioStreamConfig config) throws Exception;
}
