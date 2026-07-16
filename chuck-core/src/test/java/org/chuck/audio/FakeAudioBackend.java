package org.chuck.audio;

import java.util.List;
import org.chuck.audio.backend.AudioBackend;
import org.chuck.audio.backend.AudioBackendStream;
import org.chuck.audio.backend.AudioStreamConfig;

/**
 * In-memory {@link AudioBackend} test double: no real device I/O, so it works identically in any CI
 * environment. {@link #openStream} records the resulting {@link FakeAudioBackendStream} so a test
 * can inspect what {@link ChuckAudio}'s engine loop actually wrote/read, without needing any
 * package-private access into {@code ChuckAudio} itself.
 */
public class FakeAudioBackend implements AudioBackend {
  /** The stream most recently returned by {@link #openStream}, or {@code null} before that. */
  public volatile FakeAudioBackendStream lastStream;

  @Override
  public String name() {
    return "Fake";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public List<ChuckAudio.DeviceInfo> getOutputDeviceInfo() {
    return List.of();
  }

  @Override
  public List<ChuckAudio.DeviceInfo> getInputDeviceInfo() {
    return List.of();
  }

  @Override
  public AudioBackendStream openStream(AudioStreamConfig config) {
    FakeAudioBackendStream stream = new FakeAudioBackendStream(config);
    lastStream = stream;
    return stream;
  }
}
