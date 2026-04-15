package org.chuck.audio;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.audio.analysis.FFT;
import org.chuck.audio.analysis.Windowing;
import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;

/** Tests for Windowing static factory and FFT.window(ChuckArray) integration. */
public class WindowingTest {

  // ── Static factory tests ──────────────────────────────────────────────────

  @Test
  void testHann_length() {
    ChuckArray w = Windowing.hann(512);
    assertEquals(512, w.size());
  }

  @Test
  void testHann_endpoints() {
    ChuckArray w = Windowing.hann(512);
    // Hann window: first and last sample ≈ 0
    assertEquals(0.0, w.getFloat(0), 1e-9);
    assertEquals(0.0, w.getFloat(511), 1e-3);
  }

  @Test
  void testHann_peak() {
    int N = 512;
    ChuckArray w = Windowing.hann(N);
    // Peak at centre ≈ 1.0
    assertEquals(1.0, w.getFloat(N / 2), 1e-3);
  }

  @Test
  void testHamming_range() {
    ChuckArray w = Windowing.hamming(256);
    assertEquals(256, w.size());
    // Hamming: min ≈ 0.08, max ≈ 1.0 — all values in [0, 1]
    for (int i = 0; i < 256; i++) {
      double v = w.getFloat(i);
      assertTrue(v >= 0.0 && v <= 1.01, "hamming[" + i + "]=" + v + " out of range");
    }
  }

  @Test
  void testBlackman_length() {
    ChuckArray w = Windowing.blackman(1024);
    assertEquals(1024, w.size());
  }

  @Test
  void testBlackmanHarris_length() {
    ChuckArray w = Windowing.blackmanHarris(1024);
    assertEquals(1024, w.size());
  }

  @Test
  void testRectangular_allOnes() {
    ChuckArray w = Windowing.rectangular(64);
    assertEquals(64, w.size());
    for (int i = 0; i < 64; i++) assertEquals(1.0, w.getFloat(i), 1e-12);
  }

  @Test
  void testTriangular_symmetric() {
    int N = 101;
    ChuckArray w = Windowing.triangular(N);
    assertEquals(N, w.size());
    // Symmetric: w[i] ≈ w[N-1-i]
    for (int i = 0; i < N / 2; i++) {
      assertEquals(w.getFloat(i), w.getFloat(N - 1 - i), 1e-9);
    }
    // Peak at centre = 1.0
    assertEquals(1.0, w.getFloat(N / 2), 1e-9);
  }

  @Test
  void testHanning_aliasOfHann() {
    ChuckArray a = Windowing.hann(128);
    ChuckArray b = Windowing.hanning(128);
    for (int i = 0; i < 128; i++) assertEquals(a.getFloat(i), b.getFloat(i), 1e-12);
  }

  // ── FFT.window(ChuckArray) integration ────────────────────────────────────

  @Test
  void testFftWindowAcceptsChuckArray() {
    FFT fft = new FFT(512);
    ChuckArray w = Windowing.hann(512);
    ChuckArray ret = fft.window(w);
    assertSame(w, ret, "window() should return the same ChuckArray");
  }

  @Test
  void testFftWindowNullSafe() {
    FFT fft = new FFT(512);
    assertNull(fft.window(null)); // must not throw
  }

  // ── ChucK script integration ───────────────────────────────────────────────

  @Test
  void testChuckScript_windowingHann() {
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    String code = "FFT fft;\n" + "Windowing.hann(512) => fft.window;\n" + "<<< \"ok\" >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(5);
    assertTrue(out.toString().contains("ok"), "Windowing.hann => fft.window failed: " + out);
  }

  @Test
  void testChuckScript_windowingHamming() {
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    String code = "FFT fft;\n" + "Windowing.hamming(1024) => fft.window;\n" + "<<< \"ok\" >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(5);
    assertTrue(out.toString().contains("ok"), "Windowing.hamming => fft.window failed: " + out);
  }

  @Test
  void testChuckScript_windowingBlackman() {
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    String code = "FFT fft;\n" + "Windowing.blackman(512) => fft.window;\n" + "<<< \"ok\" >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(5);
    assertTrue(out.toString().contains("ok"), "Windowing.blackman failed: " + out);
  }

  @Test
  void testChuckScript_windowStoredAndAccessed() {
    // Verify that Windowing can be used in a realistic analysis chain
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    String code =
        "SinOsc s => FFT fft => blackhole;\n"
            + "Windowing.hann(512) => fft.window;\n"
            + "440.0 => s.freq;\n"
            + "<<< \"ready\" >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(5);
    assertTrue(out.toString().contains("ready"), "analysis chain setup failed: " + out);
  }
}
