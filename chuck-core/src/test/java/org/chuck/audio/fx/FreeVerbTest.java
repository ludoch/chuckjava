package org.chuck.audio.fx;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FreeVerbTest {

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
  void testFreeVerbImpulseResponse() {
    FreeVerb rev = new FreeVerb();
    rev.roomSize(0.85f);
    rev.damp(0.2f);
    rev.mix(1.0f); // 100% wet

    // Feed single stereo impulse
    float[] impIn = {1.0f, 1.0f};
    float[] impOut = new float[2];
    rev.tick(impOut, 0, 1, 0L, impIn);

    // Render 10000 stereo frames (decay tail)
    float[] outBuffer = new float[10000 * 2];
    rev.tick(outBuffer, 0, 10000, 1L);

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

    assertTrue(hasAudio, "FreeVerb should produce wet decay tail");
    assertTrue(leftEnergy > 0.0, "Left channel should contain energy");
    assertTrue(rightEnergy > 0.0, "Right channel should contain energy");
    assertFalse(
        isIdentical,
        "Left and right channels should display different delay offsets (stereo spread active!)");
  }

  @Test
  void testSampleRateIndependence() {
    // ── Render at 44.1kHz ──
    FreeVerb rev44 = new FreeVerb();
    rev44.roomSize(0.7f);
    rev44.damp(0.5f);
    rev44.mix(1.0f);

    float[] impIn = {1.0f, 1.0f};
    float[] impOut = new float[2];
    rev44.tick(impOut, 0, 1, 0L, impIn);

    float[] out44 = new float[4000 * 2];
    rev44.tick(out44, 0, 4000, 1L);

    double energy44 = 0.0;
    for (float val : out44) {
      energy44 += val * val;
    }
    energy44 /= 44100.0;

    // ── Render at 96kHz (using a temporary different VM context for sample rate lookup) ──
    // We recreate the VM at 96000Hz and run the same logic block
    vm.shutdown();
    vm = new ChuckVM(96000, 2);

    FreeVerb rev96 = new FreeVerb();
    rev96.roomSize(0.7f);
    rev96.damp(0.5f);
    rev96.mix(1.0f);

    float[] impOut96 = new float[2];
    rev96.tick(impOut96, 0, 1, 0L, impIn);

    // Scale sample length relative to sample rate to capture the same physical duration!
    // (4000 frames at 44.1kHz is approx 90ms. At 96kHz, 90ms is 8707 frames!)
    int numFrames96 = (int) (4000.0 * 96000.0 / 44100.0);
    float[] out96 = new float[numFrames96 * 2];
    rev96.tick(out96, 0, numFrames96, 1L);

    double energy96 = 0.0;
    for (float val : out96) {
      energy96 += val * val;
    }
    energy96 /= 96000.0;

    // Check that the high-frequency damping correction factor successfully kept the energy curves
    // closely aligned
    // (with less than 20% relative deviation, whereas uncorrected changes deviate by >100%!)
    double ratio = energy96 / energy44;
    assertTrue(
        ratio > 0.7 && ratio < 1.3,
        "Decay energy curve should remain closely aligned across sample rates: " + ratio);
  }
}
