package org.chuck.audio;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class AudioGoldenTest {

  @Test
  public void testSinOscGolden() {
    assertTrue(AudioGoldenTester.verifySinOsc(), "SinOsc RMS should be close to 0.707");
  }

  @Test
  public void testLPFGolden() {
    assertTrue(AudioGoldenTester.verifyLPF(), "LPF should attenuate high frequencies");
  }

  @Test
  public void testHPFGolden() {
    assertTrue(AudioGoldenTester.verifyHPF(), "HPF should attenuate low frequencies");
  }

  @Test
  public void testBPFGolden() {
    assertTrue(AudioGoldenTester.verifyBPF(), "BPF should pass center frequency");
  }
}
