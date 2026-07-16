package org.chuck.audio;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.audio.backend.AudioStreamConfig;
import org.junit.jupiter.api.Test;

/** Sanity-checks the fakes themselves in isolation, before {@link ChuckAudio} ever touches them. */
public class FakeAudioBackendStreamTest {

  private static AudioStreamConfig config() {
    return new AudioStreamConfig(
        "", "", 44100, 2, 0, 512, 2, org.chuck.audio.AudioSampleFormat.INT16, false, false);
  }

  @Test
  public void testWriteOutputTracksRms() {
    FakeAudioBackend backend = new FakeAudioBackend();
    var stream = backend.openStream(config());
    assertSame(stream, backend.lastStream);

    stream.start();
    float[] silence = new float[512 * 2];
    stream.writeOutput(silence, 0, silence.length);
    assertEquals(0.0, backend.lastStream.lastRms(), 1e-9);

    float[] tone = new float[512 * 2];
    for (int i = 0; i < tone.length; i++) tone[i] = 0.5f;
    stream.writeOutput(tone, 0, tone.length);
    assertEquals(0.5, backend.lastStream.lastRms(), 1e-6);
    assertTrue(backend.lastStream.maxRmsSeen() >= 0.5);
    assertEquals(2, backend.lastStream.writeCount());
  }

  @Test
  public void testReadInputReturnsSilence() {
    FakeAudioBackendStream stream = new FakeAudioBackendStream(config());
    stream.start();
    short[] buf = new short[256];
    java.util.Arrays.fill(buf, (short) 123);
    int read = stream.readInput(buf, 0, 256);
    assertEquals(256, read);
    for (short s : buf) assertEquals(0, s);
  }

  @Test
  public void testArtificialDelayActuallyDelays() {
    FakeAudioBackendStream stream = new FakeAudioBackendStream(config());
    stream.start();
    stream.artificialWriteDelayNanos = 20_000_000; // 20ms
    long start = System.nanoTime();
    stream.writeOutput(new float[8], 0, 8);
    long elapsed = System.nanoTime() - start;
    assertTrue(elapsed >= 15_000_000, "expected >=15ms elapsed, got " + elapsed / 1_000_000 + "ms");
  }
}
