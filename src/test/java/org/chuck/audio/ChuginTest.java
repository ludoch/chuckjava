package org.chuck.audio;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.chuck.audio.chugins.*;
import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;

/**
 * Smoke tests for Java-ported chugins. Each test: (1) instantiates directly and verifies the DSP
 * produces non-zero output, and (2) compiles + runs a short ChucK snippet through the VM to verify
 * end-to-end integration.
 */
public class ChuginTest {

  private static final float SR = 44100f;

  // ──────────────────────────────────────────────────────────────────────────
  // Direct DSP unit tests
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void testMagicSine_producesOscillation() {
    MagicSine ms = new MagicSine(SR);
    ms.freq(440.0);
    double rms = rms(ms, 1000);
    assertTrue(rms > 0.01, "MagicSine should produce signal, got rms=" + rms);
  }

  @Test
  void testOverdrive_clipsAtHighDrive() {
    Overdrive od = new Overdrive();
    od.drive(5.0f);
    float out = od.tick(0.5f, 0);
    assertTrue(out > 0.9f, "Overdrive drive=5 should heavily saturate 0.5 → near 1.0, got " + out);
  }

  @Test
  void testOverdrive_passThrough_atDriveOne() {
    Overdrive od = new Overdrive();
    od.drive(1.0f);
    assertEquals(0.5f, od.tick(0.5f, 0), 1e-5f);
  }

  @Test
  void testFoldbackSaturator_folds() {
    FoldbackSaturator fb = new FoldbackSaturator();
    fb.threshold(0.5f);
    // input > threshold should not return input unchanged
    float out = fb.tick(0.9f, 0);
    assertNotEquals(0.9f, out, 0.01f, "FoldbackSaturator should fold signal above threshold");
    assertTrue(Math.abs(out) <= 2.0f, "Folded output should be bounded");
  }

  @Test
  void testBitcrusher_reducesResolution() {
    Bitcrusher bc = new Bitcrusher();
    bc.bits(4);
    // With 4 bits, output should be quantized — not equal to 0.3f exactly
    float out = bc.tick(0.3f, 0);
    assertNotEquals(0.3f, out, 1e-4f, "4-bit crusher should quantize 0.3");
  }

  @Test
  void testBitcrusher_32bits_isTransparent() {
    Bitcrusher bc = new Bitcrusher();
    bc.bits(32);
    bc.downsample(1);
    float out = bc.tick(0.5f, 0);
    assertEquals(0.5f, out, 0.001f, "32-bit crusher should be transparent");
  }

  @Test
  void testExpEnv_decays() {
    ExpEnv env = new ExpEnv(SR);
    env.T60(SR * 0.1); // 100ms T60
    env.keyOn();
    float first = env.tick(1.0f, 0);
    float later = 1.0f;
    for (int i = 0; i < 4410; i++) later = env.tick(1.0f, i + 1);
    assertTrue(later < first * 0.9f, "ExpEnv should decay over time");
  }

  @Test
  void testPowerADSR_attack_increases() {
    PowerADSR env = new PowerADSR(SR);
    env.attackTime(SR * 0.1);
    env.decayTime(SR * 0.1);
    env.sustainLevel(0.5);
    env.releaseTime(SR * 0.1);
    env.keyOn();
    float first = env.tick(1.0f, 0);
    float mid = 1.0f;
    for (int i = 0; i < 2000; i++) mid = env.tick(1.0f, i + 1);
    assertTrue(mid > first, "PowerADSR attack should increase envelope");
  }

  @Test
  void testWPDiodeLadder_attenuatesHighFreq() {
    WPDiodeLadder f = new WPDiodeLadder(SR);
    f.cutoff(200.0); // very low cutoff
    // feed 4kHz sine, expect significant attenuation
    double freq = 4000.0;
    double rmsIn = 0.0, rmsOut = 0.0;
    for (int i = 0; i < 2000; i++) {
      float s = (float) Math.sin(2.0 * Math.PI * freq / SR * i);
      rmsIn += s * s;
      float o = f.tick(s, i);
      rmsOut += o * o;
    }
    assertTrue(rmsOut < rmsIn * 0.1, "DiodeLadder LP@200Hz should strongly attenuate 4kHz");
  }

  @Test
  void testWPKorg35_attenuatesHighFreq() {
    WPKorg35 f = new WPKorg35(SR);
    f.cutoff(200.0);
    double freq = 4000.0;
    double rmsIn = 0.0, rmsOut = 0.0;
    for (int i = 0; i < 2000; i++) {
      float s = (float) Math.sin(2.0 * Math.PI * freq / SR * i);
      rmsIn += s * s;
      float o = f.tick(s, i);
      rmsOut += o * o;
    }
    assertTrue(rmsOut < rmsIn * 0.1, "WPKorg35 LP@200Hz should strongly attenuate 4kHz");
  }

  @Test
  void testFIR_movingAverage_smooths() {
    FIR fir = new FIR(SR);
    fir.order(8); // 8-tap moving average
    // FIR computes output from delay buffer first, then shifts input in.
    // So tick(impulse) fills slot 0 and returns 0; tick(0) then returns impulse/8.
    fir.tick(1.0f, 0);
    float out = fir.tick(0.0f, 1);
    assertEquals(1.0f / 8, out, 1e-5f, "8-tap moving average of impulse should be 1/8");
  }

  @Test
  void testKasFilter_producesOutput() {
    KasFilter f = new KasFilter(SR);
    f.freq(1000.0);
    double rms = 0.0;
    for (int i = 0; i < 4410; i++) {
      float s = (float) Math.sin(2.0 * Math.PI * 440.0 / SR * i);
      float o = f.tick(s, i);
      rms += o * o;
    }
    rms = Math.sqrt(rms / 4410);
    assertTrue(rms > 0.001, "KasFilter should pass signal, got rms=" + rms);
  }

  @Test
  void testWinFuncEnv_attack_ramps() {
    WinFuncEnv env = new WinFuncEnv();
    env.attack(1000);
    env.release(1000);
    env.keyOn();
    float first = env.tick(1.0f, 0);
    float mid = 1.0f;
    for (int i = 1; i < 500; i++) mid = env.tick(1.0f, i);
    assertTrue(mid > first, "WinFuncEnv attack should rise from 0");
    assertTrue(mid <= 1.0f, "WinFuncEnv attack should stay ≤ 1");
  }

  @Test
  void testExpDelay_passesSignal() {
    ExpDelay ed = new ExpDelay(SR);
    ed.mix(0.5f);
    ed.reps(4);
    ed.delay(SR * 0.1); // 100ms delay
    double rmsOut = 0.0;
    for (int i = 0; i < 4410; i++) {
      float s = (float) Math.sin(2.0 * Math.PI * 440.0 / SR * i);
      float o = ed.tick(s, i);
      rmsOut += o * o;
    }
    assertTrue(rmsOut > 0, "ExpDelay should pass signal");
  }

  @Test
  void testPerlin_producesNoise() {
    Perlin p = new Perlin(SR);
    p.freq(220.0);
    double rms = rms(p, 4410);
    assertTrue(rms > 0.001, "Perlin should produce non-zero signal, got rms=" + rms);
  }

  @Test
  void testChuginRange_rescales() {
    ChuginRange r = new ChuginRange();
    r.inMin(-1.0f);
    r.inMax(1.0f);
    r.outMin(0.0f);
    r.outMax(100.0f);
    assertEquals(50.0f, r.tick(0.0f, 0), 1e-3f, "Range: 0 in [-1,1] → 50 in [0,100]");
    assertEquals(0.0f, r.tick(-1.0f, 1), 1e-3f, "Range: -1 → 0");
    assertEquals(100.0f, r.tick(1.0f, 2), 1e-3f, "Range: 1 → 100");
  }

  // ──────────────────────────────────────────────────────────────────────────
  // VM integration smoke tests (compile + run ChucK snippets)
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void testMagicSine_inVM() throws Exception {
    assertVmRmsAbove(0.01, "MagicSine s => dac; 0.5 => s.gain; 440 => s.freq; 100::ms => now;");
  }

  @Test
  void testExpEnv_inVM() throws Exception {
    assertVmRmsAbove(
        0.001,
        "SinOsc s => ExpEnv e => dac; 0.7 => s.gain; "
            + "4410.0 => e.T60; e.keyOn(); 100::ms => now;");
  }

  @Test
  void testPowerADSR_inVM() throws Exception {
    assertVmRmsAbove(
        0.001,
        "SinOsc s => PowerADSR e => dac; 0.8 => s.gain; "
            + "2205.0 => e.attackTime; 2205.0 => e.decayTime; 0.5 => e.sustainLevel; "
            + "2205.0 => e.releaseTime; e.keyOn(); 100::ms => now;");
  }

  @Test
  void testWPDiodeLadder_inVM() throws Exception {
    assertVmRmsAbove(
        0.001,
        "SawOsc s => WPDiodeLadder f => dac; 0.5 => s.gain; "
            + "110 => s.freq; 1000 => f.cutoff; 100::ms => now;");
  }

  @Test
  void testWPKorg35_inVM() throws Exception {
    assertVmRmsAbove(
        0.001,
        "SawOsc s => WPKorg35 f => dac; 0.5 => s.gain; "
            + "110 => s.freq; 1000 => f.cutoff; 100::ms => now;");
  }

  @Test
  void testPerlin_inVM() throws Exception {
    assertVmRmsAbove(0.001, "Perlin p => dac; 0.5 => p.gain; 220 => p.freq; 100::ms => now;");
  }

  @Test
  void testKasFilter_inVM() throws Exception {
    assertVmRmsAbove(
        0.001,
        "SawOsc s => KasFilter f => dac; 0.5 => s.gain; "
            + "110 => s.freq; 800 => f.freq; 100::ms => now;");
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Helpers
  // ──────────────────────────────────────────────────────────────────────────

  private double rms(ChuckUGen ugen, int samples) {
    double acc = 0.0;
    for (int i = 0; i < samples; i++) {
      float v = ugen.tick(0.0f, i);
      acc += v * v;
    }
    return Math.sqrt(acc / samples);
  }

  private void assertVmRmsAbove(double minRms, String ckCode) throws Exception {
    ChuckVM vm = new ChuckVM((int) SR);
    ByteArrayOutputStream vmOut = new ByteArrayOutputStream();
    PrintStream oldOut = System.out;
    System.setOut(new PrintStream(vmOut));
    try {
      vm.run(ckCode, "test");
      int samples = (int) (SR * 0.15);
      float[][] dac = new float[2][samples];
      vm.advanceTime(dac, 0, samples);
      System.setOut(oldOut);
      double rms = 0.0;
      for (float v : dac[0]) rms += v * v;
      rms = Math.sqrt(rms / samples);
      assertTrue(
          rms > minRms,
          "VM snippet should produce audio rms>"
              + minRms
              + ", got "
              + rms
              + ": "
              + ckCode
              + (vmOut.size() > 0 ? "\nVM output: " + vmOut : ""));
    } finally {
      System.setOut(oldOut);
    }
  }
}
