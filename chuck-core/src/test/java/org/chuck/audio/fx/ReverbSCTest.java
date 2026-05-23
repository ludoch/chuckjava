package org.chuck.audio.fx;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ReverbSCTest {

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
  void testReverbImpulseResponse() {
    ReverbSC rev = new ReverbSC();
    rev.feedback(0.85f);
    rev.lpFreq(10000.0f);
    rev.mix(1.0f); // 100% wet for analysis

    // Feed a single 1.0f impulse into both channels
    float[] impIn = {1.0f, 1.0f};
    float[] impOut = new float[2];
    rev.tick(impOut, 0, 1, 0L, impIn);

    // Render 10000 stereo frames (approx 226ms at 44.1kHz)
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

    assertTrue(hasAudio, "ReverbSC should produce diffuse reverberant tail post-impulse");
    assertTrue(leftEnergy > 0.0, "Left channel should contain energy");
    assertTrue(rightEnergy > 0.0, "Right channel should contain energy");
    assertFalse(
        isIdentical,
        "Left and right channels should be spatially distinct (lossless scattering matrix action!)");
  }

  @Test
  void testDecayTimeScaling() {
    // ── Low Feedback Reverb ──
    ReverbSC shortRev = new ReverbSC();
    shortRev.feedback(0.3f); // Very short decay
    shortRev.mix(1.0f);

    float[] shortIn = {1.0f, 1.0f};
    float[] shortOut = new float[2];
    shortRev.tick(shortOut, 0, 1, 0L, shortIn);

    float[] shortOutTail = new float[8000 * 2];
    shortRev.tick(shortOutTail, 0, 8000, 1L);

    double shortEnergy = 0.0;
    for (float val : shortOutTail) {
      shortEnergy += val * val;
    }

    // ── High Feedback Reverb ──
    ReverbSC longRev = new ReverbSC();
    longRev.feedback(0.95f); // Very long decay
    longRev.mix(1.0f);

    float[] longIn = {1.0f, 1.0f};
    float[] longOut = new float[2];
    longRev.tick(longOut, 0, 1, 0L, longIn);

    float[] longOutTail = new float[8000 * 2];
    longRev.tick(longOutTail, 0, 8000, 1L);

    double longEnergy = 0.0;
    for (float val : longOutTail) {
      longEnergy += val * val;
    }

    // High feedback tail energy must be significantly higher than low feedback tail energy!
    assertTrue(
        longEnergy > shortEnergy * 3.0,
        "Longer feedback should produce a dramatically higher tail energy decay curve");
  }
}
