package org.chuck.audio.util;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MorphingWavetableTest {
  private ChuckVM vm;
  private MorphingWavetable wt;

  @BeforeEach
  void setUp() {
    System.setProperty("chuck.audio.dummy", "true");
    vm = new ChuckVM(44100, 2);
    wt = new MorphingWavetable(44100);
  }

  @AfterEach
  void tearDown() {
    if (vm != null) vm.shutdown();
  }

  @Test
  void testMorphIndex0() {
    wt.index(0.0);
    wt.freq(440.0);

    float[] buffer = new float[1024];
    wt.tick(buffer, 0, 1024, 0);

    boolean hasAudio = false;
    for (float v : buffer) {
      if (Math.abs(v) > 0.0001f) {
        hasAudio = true;
        break;
      }
    }
    assertTrue(hasAudio, "Oscillator should produce audio at index 0");
  }

  @Test
  void testMorphIndex1() {
    wt.index(1.0);
    wt.freq(440.0);

    float[] buffer = new float[1024];
    wt.tick(buffer, 0, 1024, 0);

    boolean hasAudio = false;
    for (float v : buffer) {
      if (Math.abs(v) > 0.0001f) {
        hasAudio = true;
        break;
      }
    }
    assertTrue(hasAudio, "Oscillator should produce audio at index 1");
  }

  @Test
  void testMorphIndexInterpolation() {
    wt.index(0.5); // Blended wave
    wt.freq(440.0);

    float[] buffer = new float[1024];
    wt.tick(buffer, 0, 1024, 0);

    boolean hasAudio = false;
    for (float v : buffer) {
      if (Math.abs(v) > 0.0001f) {
        hasAudio = true;
        break;
      }
    }
    assertTrue(hasAudio, "Oscillator should produce audio at interpolated index");
  }
}
