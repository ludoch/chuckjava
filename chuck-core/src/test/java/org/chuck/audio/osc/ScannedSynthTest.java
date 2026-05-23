package org.chuck.audio.osc;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ScannedSynthTest {

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
  void testScannedSynthExcitationAndDecay() {
    ScannedSynth synth = new ScannedSynth();
    synth.freq(200.0f);
    synth.stiffness(0.15f);
    synth.damping(0.05f);
    synth.centering(0.01f);
    synth.amp(1.0f);

    // Strike the string with a medium force pluck at the exact center (0.5)
    synth.pluck(0.5, 0.8);

    // Render the initial 2000 samples (first tail segment)
    float[] tail1 = new float[2000];
    synth.tick(tail1, 0, 2000, 0L);

    double energy1 = 0.0;
    boolean hasAudio = false;
    for (float val : tail1) {
      if (Math.abs(val) > 0.0001f) {
        hasAudio = true;
      }
      energy1 += val * val;
    }

    assertTrue(hasAudio, "ScannedSynth should produce wet physical sound waves post-pluck");

    // Render another 2000 samples (decaying tail segment)
    float[] tail2 = new float[2000];
    synth.tick(tail2, 0, 2000, 2000L);

    double energy2 = 0.0;
    for (float val : tail2) {
      energy2 += val * val;
    }

    System.out.println("=== SCANNED SYNTHESIS PHYSICAL TAIL DECAY ===");
    System.out.println("  Initial tail segment energy: " + energy1);
    System.out.println("  Decayed tail segment energy: " + energy2);
    System.out.println("=============================================");

    // In a physical string model, the energy of the wave must decrease over time due to damping
    // loss!
    assertTrue(
        energy2 < energy1,
        "String wave physical energy must naturally decay due to damping: "
            + energy2
            + " vs "
            + energy1);
  }

  @Test
  void testCustomTrajectoryScanning() {
    ScannedSynth synth = new ScannedSynth();
    synth.freq(300.0f);
    synth.amp(1.0f);

    // Configure a simple reverse trajectory mapping: scans the string backwards!
    int[] reverseMap = new int[128];
    for (int i = 0; i < 128; i++) {
      reverseMap[i] = 127 - i;
    }
    synth.trajectory(reverseMap);

    float[] out = new float[1000];
    try {
      synth.tick(out, 0, 1000, 0L);
    } catch (Exception e) {
      fail("ScannedSynth should remain stable and fully functional under custom trajectories");
    }

    boolean hasAudio = false;
    for (float val : out) {
      if (Math.abs(val) > 0.0001f) {
        hasAudio = true;
      }
      assertTrue(val >= -1.5f && val <= 1.5f, "Output levels must be safely bounded: " + val);
    }
    assertTrue(hasAudio, "Reverse scanning trajectory should produce active physical waveforms");
  }
}
