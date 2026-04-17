package org.chuck;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Paths;
import org.chuck.core.ChuckDSL;
import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;

/**
 * Tests for Java DSL examples in examples_dsl/.
 *
 * <p>Each test: - Compiles and loads the DSL file via ChuckDSL.load() - Sporks the shred into a
 * headless ChuckVM - Advances simulated time and verifies expected behaviour
 */
public class DslExamplesTest {

  private static final int SAMPLE_RATE = 44100;
  private static final int BUFFER_SIZE = 512;

  /**
   * Run the VM for {@code seconds} of simulated audio, returning the peak per-buffer RMS seen
   * across all DAC channels.
   */
  private double runAndMeasure(ChuckVM vm, double seconds) {
    double maxRms = 0.0;
    long totalSamples = (long) (seconds * SAMPLE_RATE);
    for (long i = 0; i < totalSamples; i++) {
      vm.advanceTime(1);
      float sumSq = 0;
      for (int c = 0; c < vm.getNumChannels(); c++) {
        float s = vm.getChannelLastOut(c);
        sumSq += s * s;
      }
      double rms = Math.sqrt(sumSq / vm.getNumChannels());
      if (rms > maxRms) maxRms = rms;
    }
    return maxRms;
  }

  // -----------------------------------------------------------------------

  @Test
  public void testSineDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/SineDSL.java")));

    double maxRms = runAndMeasure(vm, 3.0);
    assertTrue(maxRms > 0.001, "SineDSL produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testFmDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/FmDSL.java")));

    double maxRms = runAndMeasure(vm, 2.0);
    assertTrue(maxRms > 0.001, "FmDSL produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testAdsrDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/AdsrDSL.java")));

    // 4 notes × ~1.3 s each ≈ 5 s; allow 7 s
    double maxRms = runAndMeasure(vm, 7.0);
    assertTrue(maxRms > 0.001, "AdsrDSL produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testEnvelopeDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/EnvelopeDSL.java")));

    // 3 iterations × 1.6 s = 4.8 s; allow 6 s
    double maxRms = runAndMeasure(vm, 6.0);
    assertTrue(maxRms > 0.001, "EnvelopeDSL produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testLfoDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/LfoDSL.java")));

    // LFO output goes to blackhole — no DAC signal expected.
    // Just verify the shred runs to completion without error.
    // 10 steps × 50 ms = 0.5 s; allow 2 s
    assertDoesNotThrow(
        () -> runAndMeasure(vm, 2.0), "LfoDSL should run without throwing exceptions");
  }

  @Test
  public void testPhasorDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/PhasorDSL.java")));

    // 200 steps × 10 ms = 2 s; allow 3 s
    double maxRms = runAndMeasure(vm, 3.0);
    assertTrue(maxRms > 0.001, "PhasorDSL produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testCombDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/CombDSL.java")));

    // Impulse response rings out for ~20 s; only run 2 s to verify output
    double maxRms = runAndMeasure(vm, 2.0);
    assertTrue(maxRms > 0.0001, "CombDSL produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testChirpDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/ChirpDSL.java")));

    // 1 s + 1.5 s = 2.5 s total; allow 4 s
    double maxRms = runAndMeasure(vm, 4.0);
    assertTrue(maxRms > 0.001, "ChirpDSL produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testBlitDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/BlitDSL.java")));

    // 8 notes × 120 ms ≈ 1 s; allow 3 s
    double maxRms = runAndMeasure(vm, 3.0);
    assertTrue(maxRms > 0.001, "BlitDSL produced no sound (maxRms=" + maxRms + ")");
  }

  // -----------------------------------------------------------------------
  // New examples

  @Test
  public void testHarmonicsDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/HarmonicsDSL.java")));

    // 2 sweeps × 12 notes × 125 ms = 3 s; allow 4 s
    double maxRms = runAndMeasure(vm, 4.0);
    assertTrue(maxRms > 0.001, "HarmonicsDSL produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testWindDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/WindDSL.java")));

    // 300 steps × 5 ms = 1.5 s; allow 3 s
    double maxRms = runAndMeasure(vm, 3.0);
    assertTrue(maxRms > 0.001, "WindDSL produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testOscillatorsDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/OscillatorsDSL.java")));

    // 20 notes × ~300 ms ≈ 6 s; allow 8 s
    double maxRms = runAndMeasure(vm, 8.0);
    assertTrue(maxRms > 0.001, "OscillatorsDSL produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testFm2DSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/Fm2DSL.java")));

    // 3 s sustain; allow 4 s
    double maxRms = runAndMeasure(vm, 4.0);
    assertTrue(maxRms > 0.001, "Fm2DSL produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testBlit2DSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/Blit2DSL.java")));

    // 12 notes × 150 ms ≈ 1.8 s; allow 3 s
    double maxRms = runAndMeasure(vm, 3.0);
    assertTrue(maxRms > 0.001, "Blit2DSL produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testLarryDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/LarryDSL.java")));

    // 15 impulses × 99 ms ≈ 1.5 s; allow 3 s
    double maxRms = runAndMeasure(vm, 3.0);
    assertTrue(maxRms > 0.001, "LarryDSL produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testLpfDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/LpfDSL.java")));

    // 400 steps × 5 ms = 2 s; allow 3 s
    double maxRms = runAndMeasure(vm, 3.0);
    assertTrue(maxRms > 0.001, "LpfDSL produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testBpfDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/BpfDSL.java")));

    // 400 steps × 5 ms = 2 s; allow 3 s
    double maxRms = runAndMeasure(vm, 3.0);
    assertTrue(maxRms > 0.001, "BpfDSL produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testResonZDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/ResonZDSL.java")));

    // 400 steps × 5 ms = 2 s; allow 3 s
    double maxRms = runAndMeasure(vm, 3.0);
    assertTrue(maxRms > 0.001, "ResonZDSL produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testChorusDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/ChorusDSL.java")));

    // 3 s sustain; allow 4 s
    double maxRms = runAndMeasure(vm, 4.0);
    assertTrue(maxRms > 0.001, "ChorusDSL produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testClarDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/ClarDSL.java")));

    // 9 notes × 300 ms + 500 ms ≈ 3.2 s; allow 5 s
    double maxRms = runAndMeasure(vm, 5.0);
    assertTrue(maxRms > 0.001, "ClarDSL produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testWurleyDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/WurleyDSL.java")));

    // 10 notes × 250 ms + 500 ms = 3 s; allow 5 s
    double maxRms = runAndMeasure(vm, 5.0);
    assertTrue(maxRms > 0.001, "WurleyDSL produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testDslDemo() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/DslDemo.java")));

    double maxRms = runAndMeasure(vm, 2.0);
    assertTrue(maxRms > 0.001, "DslDemo produced no sound (maxRms=" + maxRms + ")");
  }

  @Test
  public void testPolyphonyDSL() throws Exception {
    ChuckVM vm = new ChuckVM(SAMPLE_RATE);
    vm.spork(ChuckDSL.load(Paths.get("examples_dsl/PolyphonyDSL.java")));

    double maxRms = runAndMeasure(vm, 3.0);
    assertTrue(maxRms > 0.001, "PolyphonyDSL produced no sound (maxRms=" + maxRms + ")");
  }
}
