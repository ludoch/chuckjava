package org.chuck.audio.filter;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ShelfEQTest {
  private ChuckVM vm;
  private ShelfEQ eq;

  @BeforeEach
  void setUp() {
    System.setProperty("chuck.audio.dummy", "true");
    vm = new ChuckVM(44100, 2);
    eq = new ShelfEQ(44100);
  }

  @AfterEach
  void tearDown() {
    if (vm != null) vm.shutdown();
  }

  @Test
  void testFlatResponse() {
    // Both gains at 0dB should pass signal neutrally
    eq.bassFreq(100.0);
    eq.bassGain(0.0);
    eq.trebleFreq(5000.0);
    eq.trebleGain(0.0);

    float[] buffer = new float[1024];
    buffer[0] = 1.0f; // Impulse in
    eq.tick(buffer, 0, 1024, 0);

    boolean hasAudio = false;
    for (float v : buffer) {
      if (Math.abs(v) > 0.0001f) {
        hasAudio = true;
        break;
      }
    }
    assertTrue(hasAudio, "EQ at 0dB should pass audio");
  }

  @Test
  void testBoostedResponse() {
    // Boost bass and treble to ensure no NaNs or explosions
    eq.bassFreq(80.0);
    eq.bassGain(12.0); // +12dB
    eq.trebleFreq(8000.0);
    eq.trebleGain(6.0); // +6dB

    float[] buffer = new float[1024];
    buffer[0] = 1.0f; // Impulse in
    eq.tick(buffer, 0, 1024, 0);

    for (float v : buffer) {
      assertFalse(Float.isNaN(v), "Output should not be NaN");
      assertFalse(Float.isInfinite(v), "Output should not be infinite");
    }
  }
}
