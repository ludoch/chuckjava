package org.chuck.audio.backend;

import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.chuck.audio.ChuckAudio.DeviceInfo;

/**
 * Low-latency macOS CoreAudio / AudioUnit audio driver backend implemented in 100% pure Java via
 * Project Panama FFM API (JDK 27).
 *
 * <p>Binds directly to {@code /System/Library/Frameworks/AudioToolbox.framework/AudioToolbox} to
 * negotiate low-latency (<5ms) DefaultOutputUnit audio streams without requiring JNI or C++ helper
 * libraries.
 */
public class CoreAudioBackend implements AudioBackend {
  private static final Logger logger = Logger.getLogger(CoreAudioBackend.class.getName());
  private static final Linker linker = Linker.nativeLinker();
  private static SymbolLookup audioToolbox = null;
  private static boolean available = false;

  static {
    try {
      String os = System.getProperty("os.name", "").toLowerCase();
      if (os.contains("mac") && !Boolean.getBoolean("chuck.ffm.disable")) {
        audioToolbox =
            SymbolLookup.libraryLookup(
                "/System/Library/Frameworks/AudioToolbox.framework/AudioToolbox", Arena.global());
        // Verify critical symbols are available
        if (audioToolbox.find("AudioComponentFindNext").isPresent()
            && audioToolbox.find("AudioComponentInstanceNew").isPresent()
            && audioToolbox.find("AudioUnitInitialize").isPresent()
            && audioToolbox.find("AudioOutputUnitStart").isPresent()) {
          available = true;
          logger.log(Level.INFO, "[CoreAudioBackend] Native FFM CoreAudio symbols loaded (macOS).");
        }
      }
    } catch (Throwable t) {
      logger.log(Level.FINE, "[CoreAudioBackend] Not available on this system: " + t.getMessage());
      available = false;
    }
  }

  @Override
  public String name() {
    return "CoreAudio";
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
            "Default CoreAudio Output (Low-Latency FFM)",
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
  public List<DeviceInfo> getInputDeviceInfo() {
    List<DeviceInfo> list = new ArrayList<>();
    if (!available) return list;
    list.add(
        new DeviceInfo(
            "Default CoreAudio Input (Low-Latency FFM)",
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
  public AudioBackendStream openStream(AudioStreamConfig config) throws Exception {
    if (!available) {
      throw new IllegalStateException("CoreAudioBackend is not available on this platform.");
    }
    return new CoreAudioBackendStream(config, linker, audioToolbox);
  }
}
