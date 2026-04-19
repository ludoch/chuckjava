package org.chuck.audio.util;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DelugeAdsrTest {
  private ChuckVM vm;
  private DelugeAdsr adsr;

  @BeforeEach
  void setUp() {
    System.setProperty("chuck.audio.dummy", "true");
    vm = new ChuckVM(44100, 2);
    adsr = new DelugeAdsr(44100);
  }

  @AfterEach
  void tearDown() {
    if (vm != null) vm.shutdown();
  }

  @Test
  void testAttackDecaySustainRelease() {
    // 100ms attack, 100ms decay, 0.5 sustain, 100ms release
    adsr.set(0.1, 0.1, 0.5, 0.1);

    assertEquals(DelugeAdsr.IDLE, adsr.state());

    // Trigger key on
    adsr.keyOn();
    assertEquals(DelugeAdsr.ATTACK, adsr.state());

    // Advance 100ms (attack phase)
    int attackSamples = (int) (0.1 * 44100);
    for (int i = 0; i < attackSamples; i++) {
      adsr.tick(1.0f);
    }

    // It should now be in DECAY state or close to it
    assertTrue(
        adsr.state() == DelugeAdsr.DECAY || adsr.state() == DelugeAdsr.SUSTAIN,
        "State should be DECAY or SUSTAIN after attack, but was: " + adsr.state());

    // Advance 100ms (decay phase)
    for (int i = 0; i < attackSamples; i++) {
      adsr.tick(1.0f);
    }

    assertEquals(DelugeAdsr.SUSTAIN, adsr.state());
    assertEquals(0.5f, adsr.value(), 0.05f); // approximate due to float math

    // Trigger key off
    adsr.keyOff();
    assertEquals(DelugeAdsr.RELEASE, adsr.state());

    // Advance 100ms (release phase)
    for (int i = 0; i < attackSamples; i++) {
      adsr.tick(1.0f);
    }

    assertEquals(DelugeAdsr.IDLE, adsr.state());
    assertEquals(0.0f, adsr.value(), 0.05f);
  }
}
