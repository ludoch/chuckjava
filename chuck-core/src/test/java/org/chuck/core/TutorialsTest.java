package org.chuck.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class TutorialsTest {

  private void runWithTimeout(String code, double virtualSeconds) {
    ChuckVM vm = new ChuckVM(44100, 2);
    int id = vm.run(code, "test.ck", false); // Spork it
    assertTrue(id >= 0, "Failed to run code");

    long totalSamples = (long) (virtualSeconds * 44100);
    for (long i = 0; i < totalSamples; i++) {
      vm.advanceTime(1);
    }
    vm.shutdown();
  }

  @Test
  public void testTutorial1() {
    String code = "SinOsc s => dac; 0.5 => s.gain; 440 => s.freq; 1::second => now;";
    assertDoesNotThrow(() -> runWithTimeout(code, 1.1));
  }

  @Test
  public void testTutorial2() {
    String code =
        "SinOsc s => dac; 0.5 => s.gain; [60, 62, 64, 65, 67, 69, 71, 72] @=> int major[]; "
            + "for(0 => int i; i < 4; i++) { major[i % major.cap()] => Std.mtof => s.freq; 200::ms => now; }";
    assertDoesNotThrow(() -> runWithTimeout(code, 1.0));
  }

  @Test
  public void testTutorial3() {
    String code =
        "SinOsc s => dac; fun void play(int note, dur d) { note => Std.mtof => s.freq; d => now; } "
            + "play(60, 500::ms); play(67, 500::ms);";
    assertDoesNotThrow(() -> runWithTimeout(code, 1.1));
  }

  @Test
  public void testTutorial4() {
    String code = "Impulse i => dac; for(0=>int j; j<5; j++) { 1.0 => i.next; 100::ms => now; }";
    assertDoesNotThrow(() -> runWithTimeout(code, 0.6));
  }

  @Test
  public void testTutorial5() {
    String code =
        "fun void saw() { SawOsc s => dac; 0.2 => s.gain; while(true) { 60 => Std.mtof => s.freq; 1::second => now; } } "
            + "fun void sine() { SinOsc s => dac; 0.2 => s.gain; while(true) { 67 => Std.mtof => s.freq; 1::second => now; } } "
            + "spork ~ saw(); spork ~ sine(); 500::ms => now;";
    assertDoesNotThrow(() -> runWithTimeout(code, 0.6));
  }
}
