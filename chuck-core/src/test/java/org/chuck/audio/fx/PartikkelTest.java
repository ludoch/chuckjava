package org.chuck.audio.fx;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PartikkelTest {

  private ChuckVM vm;

  @BeforeEach
  void setUp() {
    System.setProperty("chuck.audio.dummy", "true");
    vm = new ChuckVM(44100, 2); // Stereo
  }

  @AfterEach
  void tearDown() {
    if (vm != null) vm.shutdown();
  }

  @Test
  void testGranularCloudGeneration() {
    Partikkel part = new Partikkel();
    part.grainFreq(50.0f); // 50 grains per second
    part.duration(50.0f); // 50ms grain duration
    part.mix(1.0f); // 100% wet
    part.waveAmp(0, 1.0f); // Activate slot 0

    // Load a short test sawtooth cycle in slot 0
    float[] testSaw = new float[512];
    for (int i = 0; i < 512; i++) {
      testSaw[i] = -1.0f + 2.0f * i / 512.0f;
    }
    part.waveform(0, testSaw);

    // Feed a dry carrier line (which should be overridden by wet mix)
    float[] dryIn = {0.0f, 0.0f};
    float[] dryOut = new float[2];
    part.tick(dryOut, 0, 1, 0L, dryIn);

    // Render 4000 frames (approx 90ms of granular cloud!)
    float[] outBuffer = new float[4000 * 2];
    part.tick(outBuffer, 0, 4000, 1L);

    boolean hasAudio = false;
    double leftEnergy = 0.0;
    double rightEnergy = 0.0;
    boolean isIdentical = true;

    for (int i = 0; i < outBuffer.length; i += 2) {
      float l = outBuffer[i];
      float r = outBuffer[i + 1];

      if (Math.abs(l) > 0.0001f || Math.abs(r) > 0.0001f) {
        hasAudio = true;
      }
      leftEnergy += l * l;
      rightEnergy += r * r;

      if (Math.abs(l - r) > 0.0001f) {
        isIdentical = false;
      }
    }

    assertTrue(hasAudio, "Partikkel must generate active granular sound waves");
    assertTrue(leftEnergy > 0.0, "Left channel should contain energy");
    assertTrue(rightEnergy > 0.0, "Right channel should contain energy");
    assertFalse(isIdentical, "Left and right channels should be spatially pan split");
  }

  @Test
  void testDensityOverlapEnergy() {
    // ── Low Density Cloud ──
    Partikkel lowPart = new Partikkel();
    lowPart.grainFreq(10.0f); // 10 grains/sec
    lowPart.duration(30.0f); // 30ms grains
    lowPart.mix(1.0f);
    lowPart.waveAmp(0, 1.0f);

    float[] impIn = {0.0f, 0.0f};
    float[] impOut = new float[2];
    lowPart.tick(impOut, 0, 1, 0L, impIn);

    float[] outLow = new float[4000 * 2];
    lowPart.tick(outLow, 0, 4000, 1L);

    double lowEnergy = 0.0;
    for (float val : outLow) {
      lowEnergy += val * val;
    }

    // ── High Density Overlapping Cloud ──
    Partikkel highPart = new Partikkel();
    highPart.grainFreq(100.0f); // 100 grains/sec (high overlap density!)
    highPart.duration(30.0f); // 30ms grains
    highPart.mix(1.0f);
    highPart.waveAmp(0, 1.0f);

    highPart.tick(impOut, 0, 1, 0L, impIn);

    float[] outHigh = new float[4000 * 2];
    highPart.tick(outHigh, 0, 4000, 1L);

    double highEnergy = 0.0;
    for (float val : outHigh) {
      highEnergy += val * val;
    }

    // High density cloud must produce significantly more cumulative energy due to high grain
    // overlap sums!
    assertTrue(
        highEnergy > lowEnergy * 2.5,
        "High density overlapping grain scheduling must generate a higher cumulative cloud energy tail: "
            + highEnergy
            + " vs "
            + lowEnergy);
  }

  @Test
  void testStochasticJitterJniStability() {
    Partikkel part = new Partikkel();
    part.grainFreq(40.0f);
    part.distribution(0.8f); // 80% time trigger jitter!
    part.mix(1.0f);
    part.waveAmp(0, 1.0f);

    float[] out = new float[1000 * 2];
    try {
      part.tick(out, 0, 1000, 0L);
    } catch (Exception e) {
      fail("Partikkel must maintain 100% execution safety under high random distribution jitters");
    }

    boolean hasAudio = false;
    for (float val : out) {
      if (Math.abs(val) > 0.0001f) {
        hasAudio = true;
      }
      assertTrue(val >= -4.0f && val <= 4.0f, "Output levels must remain safely bounded: " + val);
    }
    assertTrue(hasAudio, "Jittered granular cloud should produce active physical waveforms");
  }
}
