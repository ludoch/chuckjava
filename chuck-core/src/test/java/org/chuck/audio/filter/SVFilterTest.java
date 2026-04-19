package org.chuck.audio.filter;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SVFilterTest {
  private ChuckVM vm;
  private SVFilter svf;

  @BeforeEach
  void setUp() {
    System.setProperty("chuck.audio.dummy", "true");
    vm = new ChuckVM(44100, 2);
    svf = new SVFilter(44100);
  }

  @AfterEach
  void tearDown() {
    if (vm != null) vm.shutdown();
  }

  @Test
  void testMorphLP() {
    svf.morph(0.0); // Pure Low-Pass
    svf.freq(1000.0);
    svf.Q(1.0);

    float[] buffer = new float[1024];
    // Input an impulse
    svf.tick(1.0f);

    // Process block
    svf.tick(buffer, 0, 1024, 0);

    // Filter should not explode and should produce output
    boolean hasAudio = false;
    for (float v : buffer) {
      if (Math.abs(v) > 0.0001f) {
        hasAudio = true;
        break;
      }
    }
    assertTrue(hasAudio, "LP mode should produce audio for an impulse");
  }

  @Test
  void testMorphHP() {
    svf.morph(1.0); // Pure High-Pass
    svf.freq(1000.0);
    svf.Q(1.0);

    float[] buffer = new float[1024];
    svf.tick(1.0f);
    svf.tick(buffer, 0, 1024, 0);

    boolean hasAudio = false;
    for (float v : buffer) {
      if (Math.abs(v) > 0.0001f) {
        hasAudio = true;
        break;
      }
    }
    assertTrue(hasAudio, "HP mode should produce audio for an impulse");
  }

  @Test
  void testMorphBP() {
    svf.morph(0.5); // Pure Band-Pass
    svf.freq(1000.0);
    svf.Q(1.0);

    float[] buffer = new float[1024];
    svf.tick(1.0f);
    svf.tick(buffer, 0, 1024, 0);

    boolean hasAudio = false;
    for (float v : buffer) {
      if (Math.abs(v) > 0.0001f) {
        hasAudio = true;
        break;
      }
    }
    assertTrue(hasAudio, "BP mode should produce audio for an impulse");
  }
}
