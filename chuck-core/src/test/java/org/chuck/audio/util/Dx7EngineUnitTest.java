package org.chuck.audio.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Standalone unit test for Dx7Engine – same package so it can call compute().
 */
public class Dx7EngineUnitTest {

  // ── Known-good test patches ──

  /**
   * Simple test patch: all 6 operators identical, outputLevel=99, coarse=1,
   * algorithm=0 (serial chain), feedback=0.
   * This should produce clearly audible output.
   */
  static final String SIMPLE_PATCH_HEX =
    "6363636363503c0000010063000000000000000000" +  // op1
    "6363636363503c0000010063000000000000000000" +  // op2
    "6363636363503c0000010063000000000000000000" +  // op3
    "6363636363503c0000010063000000000000000000" +  // op4
    "6363636363503c0000010063000000000000000000" +  // op5
    "6363636363503c0000010063000000000000000000" +  // op6
    "00000000000000000000000000000000000000" +       // globals (19 bytes)
    "5445535420202020202000";                         // name (10) + checksum (1)

  /**
   * Variation of SIMPLE_PATCH with algorithm=12 (all 6 parallel carriers).
   */
  static String simplePatchAlgo(int algo) {
    return SIMPLE_PATCH_HEX.substring(0, 134 * 2)
        + String.format("%02x", algo)
        + SIMPLE_PATCH_HEX.substring(135 * 2);
  }

  @Test
  public void testDx7EngineProducesAudio() {
    float sr = 44100f;
    Dx7Engine dx7 = new Dx7Engine(sr);
    Dx7Patch patch = Dx7Patch.fromHex(SIMPLE_PATCH_HEX);

    assertNotNull(patch);
    assertEquals(6, patch.operators().length);

    dx7.loadPatch(patch);
    dx7.setFreq(261.6f); // C4
    dx7.noteOn();

    float peak = 0;
    for (int i = 0; i < sr; i++) { // 1 second
      float sample = dx7.compute(0, i);
      float abs = Math.abs(sample);
      if (abs > peak) peak = abs;
      if (i < 10) System.out.println("sample[" + i + "] = " + sample);
    }

    System.out.println("Dx7EngineUnitTest: peak after 1s = " + peak);
    assertTrue(peak > 0.001f,
        "Dx7Engine should produce audible output (peak=" + peak + ")");
  }

  @Test
  public void testDx7EngineAllAlgorithmsProduceOutput() {
    float sr = 44100f;

    for (int algo = 0; algo < 32; algo++) {
      Dx7Engine dx7 = new Dx7Engine(sr);
      Dx7Patch patch = Dx7Patch.fromHex(simplePatchAlgo(algo));
      dx7.loadPatch(patch);
      dx7.setFreq(261.6f);
      dx7.noteOn();

      float peak = 0;
      for (int i = 0; i < sr / 4; i++) { // 0.25s per algo
        float sample = dx7.compute(0, i);
        float abs = Math.abs(sample);
        if (abs > peak) peak = abs;
      }
      System.out.println("algo " + algo + " peak = " + peak);
      assertTrue(peak > 0.0001f,
          "Algorithm " + algo + " should produce output (peak=" + peak + ")");
    }
  }

  @Test
  public void testDx7EngineNoteOnOff() {
    // Test envelope behavior
    float sr = 44100f;
    Dx7Engine dx7 = new Dx7Engine(sr);
    dx7.loadPatch(Dx7Patch.fromHex(simplePatchAlgo(12))); // all parallel
    dx7.setFreq(440f);
    dx7.noteOn();

    float peakDuring = 0;
    for (int i = 0; i < sr / 4; i++) {
      float s = dx7.compute(0, i);
      float abs = Math.abs(s);
      if (abs > peakDuring) peakDuring = abs;
    }
    System.out.println("Peak during note: " + peakDuring);
    assertTrue(peakDuring > 0.001f, "Should produce sound during note");

    dx7.noteOff();
    float peakAfter = 0;
    for (int i = 0; i < sr / 2; i++) {
      float s = dx7.compute(0, i);
      float abs = Math.abs(s);
      if (abs > peakAfter) peakAfter = abs;
    }
    System.out.println("Peak after note off: " + peakAfter);

    // After release (EG rates are 99), sound should fade significantly
    assertTrue(peakAfter <= peakDuring + 0.01f,
        "Sound should not get louder after note-off");
  }
}
