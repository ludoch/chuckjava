package org.chuck.audio.backend;

import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.chuck.audio.ChuckAudio.DeviceInfo;

/**
 * Low-latency Windows WASAPI (Exclusive / Shared Mode) audio driver backend implemented in 100%
 * pure Java via Project Panama FFM API (JDK 27).
 *
 * <p>Binds directly to {@code Ole32.dll} and {@code Avrt.dll} to boost the audio rendering thread
 * to "Pro Audio" (THREAD_PRIORITY_TIME_CRITICAL) real-time priority and achieve low-latency (<5ms)
 * hardware buffer scheduling.
 */
public class WASAPIBackend implements AudioBackend {
  private static final Logger logger = Logger.getLogger(WASAPIBackend.class.getName());
  private static final Linker linker = Linker.nativeLinker();
  private static SymbolLookup ole32 = null;
  private static SymbolLookup avrt = null;
  private static boolean available = false;

  static {
    try {
      String os = System.getProperty("os.name", "").toLowerCase();
      if (os.contains("win") && !Boolean.getBoolean("chuck.ffm.disable")) {
        ole32 = SymbolLookup.libraryLookup("Ole32.dll", Arena.global());
        try {
          avrt = SymbolLookup.libraryLookup("Avrt.dll", Arena.global());
        } catch (Throwable ignored) {
          // Avrt is optional for thread boosting
        }
        if (ole32.find("CoInitializeEx").isPresent()
            && ole32.find("CoCreateInstance").isPresent()) {
          available = true;
          logger.log(
              Level.INFO, "[WASAPIBackend] Native FFM WASAPI/Ole32 symbols loaded (Windows).");
        }
      }
    } catch (Throwable t) {
      logger.log(Level.FINE, "[WASAPIBackend] Not available on this system: " + t.getMessage());
      available = false;
    }
  }

  @Override
  public String name() {
    return "WASAPI";
  }

  @Override
  public boolean isAvailable() {
    return available;
  }

  @Override
  public List<DeviceInfo> getOutputDeviceInfo() {
    List<DeviceInfo> list = new ArrayList<>();
    if (!available) return list;
    list.add(
        new DeviceInfo(
            "Default WASAPI Output (Low-Latency Exclusive/Shared)",
            0,
            2,
            List.of(44100, 48000, 96000),
            44100,
            List.of(
                org.chuck.audio.AudioSampleFormat.FLOAT32, org.chuck.audio.AudioSampleFormat.INT16),
            List.of(
                org.chuck.audio.AudioSampleFormat.FLOAT32,
                org.chuck.audio.AudioSampleFormat.INT16)));
    return list;
  }

  @Override
  public List<DeviceInfo> getInputDeviceInfo() {
    List<DeviceInfo> list = new ArrayList<>();
    if (!available) return list;
    list.add(
        new DeviceInfo(
            "Default WASAPI Input (Low-Latency FFM)",
            2,
            0,
            List.of(44100, 48000, 96000),
            44100,
            List.of(
                org.chuck.audio.AudioSampleFormat.FLOAT32, org.chuck.audio.AudioSampleFormat.INT16),
            List.of(
                org.chuck.audio.AudioSampleFormat.FLOAT32,
                org.chuck.audio.AudioSampleFormat.INT16)));
    return list;
  }

  @Override
  public AudioBackendStream openStream(AudioStreamConfig config) throws Exception {
    if (!available) {
      throw new IllegalStateException("WASAPIBackend is not available on this platform.");
    }
    return new WASAPIBackendStream(config, linker, ole32, avrt);
  }
}
