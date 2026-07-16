package org.chuck.audio.backend;

import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.chuck.audio.ChuckAudio.DeviceInfo;

/**
 * Low-latency JACK (JACK Audio Connection Kit) driver backend implemented in 100% pure Java via
 * Project Panama FFM API (JDK 27).
 *
 * <p>Binds directly to {@code libjack.so.0} (Linux) or {@code libjack.dylib} (macOS) to register
 * real-time Pro Audio callback graphs with target latency <5ms (e.g. 64-128 sample periods).
 */
public class JackBackend implements AudioBackend {
  private static final Logger logger = Logger.getLogger(JackBackend.class.getName());
  private static final Linker linker = Linker.nativeLinker();
  private static SymbolLookup libJack = null;
  private static boolean available = false;

  static {
    try {
      if (!Boolean.getBoolean("chuck.ffm.disable")) {
        // Try standard library lookup names on Linux and macOS
        String[] libNames = {
          "jack",
          "libjack.so.0",
          "libjack.so",
          "/usr/local/lib/libjack.dylib",
          "/opt/homebrew/lib/libjack.dylib"
        };
        for (String name : libNames) {
          try {
            libJack = SymbolLookup.libraryLookup(name, Arena.global());
            if (libJack.find("jack_client_open").isPresent()
                && libJack.find("jack_set_process_callback").isPresent()
                && libJack.find("jack_activate").isPresent()) {
              available = true;
              logger.log(Level.INFO, "[JackBackend] Native FFM JACK symbols loaded from: " + name);
              break;
            }
          } catch (Throwable ignored) {
          }
        }
      }
    } catch (Throwable t) {
      logger.log(Level.FINE, "[JackBackend] Not available on this system: " + t.getMessage());
      available = false;
    }
  }

  @Override
  public String name() {
    return "JACK";
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
            "Default JACK Audio Connection Kit (Low-Latency FFM)",
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
            "Default JACK Audio Connection Kit Input (FFM)",
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
      throw new IllegalStateException("JackBackend is not available on this platform.");
    }
    return new JackBackendStream(config, linker, libJack);
  }
}
