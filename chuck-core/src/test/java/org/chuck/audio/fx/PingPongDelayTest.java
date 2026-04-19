package org.chuck.audio.fx;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PingPongDelayTest {

  private ChuckVM vm;

  @BeforeEach
  void setUp() {
    System.setProperty("chuck.audio.dummy", "true");
    vm = new ChuckVM(44100, 2);
  }

  @AfterEach
  void tearDown() {
    if (vm != null) vm.shutdown();
  }

  @Test
  void testPingPongOutput() {
    PingPongDelay pp = new PingPongDelay(44100);
    pp.time(0.1); // 100ms delay
    pp.feedback(0.5);

    // Feed an impulse into the delay
    pp.compute(1.0f, 0);

    // Create a stereo buffer to tick into
    // We need to tick at least 4410 frames (100ms at 44100Hz) to see the first delay
    float[] outBuffer = new float[10000 * 2]; // 10000 stereo frames
    pp.tick(outBuffer, 0, 10000, 0);

    // We expect the left channel to have a delay output at 100ms
    // and the right channel at 150ms.
    boolean hasL = false;
    boolean hasR = false;

    for (int i = 0; i < outBuffer.length; i += 2) {
      if (Math.abs(outBuffer[i]) > 0.001f) hasL = true;
      if (Math.abs(outBuffer[i + 1]) > 0.001f) hasR = true;
    }

    assertTrue(hasL || hasR, "PingPongDelay should produce delayed output.");
  }
}
