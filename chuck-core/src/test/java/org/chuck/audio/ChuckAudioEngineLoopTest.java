package org.chuck.audio;

import static org.chuck.core.ChuckDSL.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.function.BooleanSupplier;
import org.chuck.audio.osc.SinOsc;
import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;

/**
 * First-ever direct test of {@link ChuckAudio}'s engine-loop logic (drift/underrun detection,
 * idle-gain-fade, RMS-bearing output) - made possible by routing it through an in-memory {@link
 * FakeAudioBackend} instead of a real device, so it runs identically in any CI environment
 * regardless of {@code chuck.audio.dummy} (which {@code initBackend} deliberately ignores). Also
 * serves as the regression net for the {@code runJavaSoundLoop()}/{@code runBackendLoop()}
 * extract-method split in {@link ChuckAudio#start()}.
 */
public class ChuckAudioEngineLoopTest {

  private static final int SAMPLE_RATE = 44100;
  private static final int BUFFER_SIZE = 256;

  @Test
  void testNonSilentOutputReachesBackend() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE, 2);
    vm.setLogLevel(0);
    FakeAudioBackend backend = new FakeAudioBackend();
    ChuckAudio audio = new ChuckAudio(vm, BUFFER_SIZE, 2, SAMPLE_RATE, backend);
    assertTrue(audio.isOutputLineReady());

    vm.spork(
        () -> {
          SinOsc s = new SinOsc(sampleRate());
          s.freq(440);
          s.gain(0.5);
          s.chuck(dac());
          // Keep the shred (and therefore vm.getActiveShredCount() > 0) alive for the whole test
          // - ChuckAudio's idle-gain-fade would otherwise mute output the instant this returns.
          advance(second(5));
        });

    audio.start();
    try {
      waitUntil(() -> backend.lastStream != null && backend.lastStream.writeCount() >= 10, 2000);
      assertTrue(
          backend.lastStream.maxRmsSeen() > 0.001,
          "expected non-silent RMS, got " + backend.lastStream.maxRmsSeen());
    } finally {
      audio.stop();
    }
  }

  @Test
  void testIdleFadesToSilence() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE, 2);
    vm.setLogLevel(0);
    FakeAudioBackend backend = new FakeAudioBackend();
    ChuckAudio audio = new ChuckAudio(vm, BUFFER_SIZE, 2, SAMPLE_RATE, backend);

    // No shreds sporked at all - vm.getActiveShredCount() is 0 from the very first buffer.
    audio.start();
    try {
      waitUntil(() -> backend.lastStream != null && backend.lastStream.writeCount() >= 5, 2000);
      assertEquals(0f, audio.getPeakOut(0), 0.001f);
      assertEquals(0.0, backend.lastStream.maxRmsSeen(), 0.001);
    } finally {
      audio.stop();
    }
  }

  @Test
  void testUnderrunCounterIncrementsUnderArtificialDelay() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE, 2);
    vm.setLogLevel(0);
    FakeAudioBackend backend = new FakeAudioBackend();
    ChuckAudio audio = new ChuckAudio(vm, BUFFER_SIZE, 2, SAMPLE_RATE, backend);

    audio.start();
    try {
      waitUntil(() -> backend.lastStream != null, 2000);
      // Expected buffer period at 256 samples / 44100 Hz is ~5.8ms; force each cycle to take far
      // longer so ChuckAudio's own wall-clock drift detection (unchanged by the backend split)
      // has to notice, exactly as it would with a genuinely slow/overloaded real driver.
      backend.lastStream.artificialWriteDelayNanos = 30_000_000; // 30ms
      long before = audio.getUnderrunCount();
      waitUntil(() -> audio.getUnderrunCount() > before, 3000);
      assertTrue(audio.getUnderrunCount() > before);
    } finally {
      audio.stop();
    }
  }

  private static void waitUntil(BooleanSupplier cond, long timeoutMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (!cond.getAsBoolean()) {
      if (System.currentTimeMillis() > deadline) {
        fail("condition not met within " + timeoutMs + "ms");
      }
      Thread.sleep(10);
    }
  }
}
