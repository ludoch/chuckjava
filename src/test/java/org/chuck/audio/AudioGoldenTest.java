package org.chuck.audio;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class AudioGoldenTest {

  @Test
  public void testSinOscGolden() {
    assertTrue(AudioGoldenTester.verifySinOsc(), "SinOsc RMS should be close to 0.707");
  }
}
