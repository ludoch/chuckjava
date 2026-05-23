package org.chuck.audio.osc;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GendyTest {

  private ChuckVM vm;

  @BeforeEach
  void setUp() {
    System.setProperty("chuck.audio.dummy", "true");
    vm = new ChuckVM(44100, 1); // Mono
  }

  @AfterEach
  void tearDown() {
    if (vm != null) vm.shutdown();
  }

  @Test
  void testLinearGendySignalAndMirrorBounds() {
    Gendy g = new Gendy();
    g.mode(Gendy.MODE_LINEAR);
    g.minfreq(100.0f);
    g.maxfreq(1000.0f);
    g.ampscl(0.1f);
    g.durscl(0.1f);
    g.amp(1.0f);

    float[] out = new float[2000];
    g.tick(out, 0, 2000, 0L);

    boolean hasAudio = false;
    for (float val : out) {
      if (Math.abs(val) > 0.0001f) {
        hasAudio = true;
      }
      // Assert mirror boundaries are strictly preserved
      assertTrue(
          val >= -1.05f && val <= 1.05f,
          "Stochastic synthesis must never leak amplitude limits: " + val);
    }
    assertTrue(hasAudio, "Gendy linear step should produce active signal waveforms");
  }

  @Test
  void testPowerGendySignal() {
    Gendy g = new Gendy();
    g.mode(Gendy.MODE_POWER);
    g.curveup(3.5f);
    g.curvedown(0.5f);
    g.minfreq(100.0f);
    g.maxfreq(1000.0f);

    float[] out = new float[1000];
    g.tick(out, 0, 1000, 0L);

    boolean hasAudio = false;
    for (float val : out) {
      if (Math.abs(val) > 0.0001f) {
        hasAudio = true;
      }
      assertTrue(
          val >= -1.05f && val <= 1.05f,
          "Amplitude limits must be preserved in power mode: " + val);
    }
    assertTrue(hasAudio, "Gendy power step should produce active signal waveforms");
  }

  @Test
  void testCubicContinuity() {
    Gendy g = new Gendy();
    g.mode(Gendy.MODE_CUBIC);
    g.minfreq(100.0f);
    g.maxfreq(1000.0f);

    float[] out = new float[2000];
    g.tick(out, 0, 2000, 0L);

    // Calculate maximum delta and second-order derivative steps to verify smooth continuous
    // trajectory
    double maxStepDelta = 0.0;
    double maxSecondOrderDelta = 0.0;

    for (int i = 2; i < out.length; i++) {
      double d1 = out[i] - out[i - 1];
      double d2 = out[i - 1] - out[i - 2];
      double secondOrderDiff = Math.abs(d1 - d2);

      maxStepDelta = Math.max(maxStepDelta, Math.abs(d1));
      maxSecondOrderDelta = Math.max(maxSecondOrderDelta, secondOrderDiff);
    }

    System.out.println("=== GENDY CUBIC SECOND-DERIVATIVE TRAJECTORY FIDELITY ===");
    System.out.println("  Max first-order step difference: " + maxStepDelta);
    System.out.println("  Max second-order derivative change: " + maxSecondOrderDelta);
    System.out.println("=========================================================");

    // In cubic second-derivative interpolation, step changes are continuous and smooth
    assertTrue(
        maxSecondOrderDelta < 0.20, "Cubic interpolation steps must remain smooth and continuous");
  }

  @Test
  void testDistributionProfilesStability() {
    Gendy g = new Gendy();
    g.ampscl(0.1f);
    g.durscl(0.1f);

    // Run through all standard distribution profiles: Cauchy(1), Logistic(2), HyperbolicCos(3),
    // Arcsine(4), Exponential(5)
    for (int dist = 0; dist <= 5; dist++) {
      g.ampdist(dist);
      g.durdist(dist);

      float[] out = new float[500];
      try {
        g.tick(out, 0, 500, 0L);
      } catch (Exception e) {
        fail("Gendy should remain completely stable across distribution profiles: " + dist);
      }

      for (float val : out) {
        assertTrue(
            val >= -1.05f && val <= 1.05f,
            "Distribution: " + dist + " leaked bounds limit: " + val);
      }
    }
  }
}
