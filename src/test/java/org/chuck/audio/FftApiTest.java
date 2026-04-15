package org.chuck.audio;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.audio.analysis.FFT;
import org.chuck.audio.analysis.IFFT;
import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;

/** Tests for the expanded FFT/IFFT/UAnaBlob ChucK API. */
public class FftApiTest {

  // ── FFT.size() getter / setter ────────────────────────────────────────────

  @Test
  void testFftSizeGetter() {
    FFT fft = new FFT(512);
    assertEquals(512L, fft.size());
  }

  @Test
  void testFftSizeLongSetter() {
    FFT fft = new FFT(512);
    fft.size(1024L);
    assertEquals(1024L, fft.size());
  }

  @Test
  void testFftSizeDoubleSetter() {
    FFT fft = new FFT(512);
    fft.size(256.0);
    assertEquals(256L, fft.size());
  }

  // ── FFT.cval(n) ───────────────────────────────────────────────────────────

  @Test
  void testFftCvalReturnsComplex() {
    FFT fft = new FFT(8);
    // Feed some samples and upchuck
    for (int i = 0; i < 16; i++) fft.tick(0.5f, i);
    fft.upchuck();
    ChuckArray c = fft.cval(0);
    assertNotNull(c);
    assertEquals("complex", c.vecTag);
    assertEquals(2, c.size());
  }

  @Test
  void testFftCvalOutOfRange() {
    FFT fft = new FFT(8);
    ChuckArray c = fft.cval(999L);
    // Should return zero-filled complex, not throw
    assertNotNull(c);
    assertEquals(0.0, c.getFloat(0), 1e-9);
    assertEquals(0.0, c.getFloat(1), 1e-9);
  }

  // ── FFT.spectrum(ChuckArray) ───────────────────────────────────────────────

  @Test
  void testFftSpectrum() {
    FFT fft = new FFT(8);
    for (int i = 0; i < 16; i++) fft.tick(0.3f, i);
    fft.upchuck();
    // Pre-allocate complex array of size/2 = 4
    ChuckArray s = new ChuckArray("complex", 4);
    ChuckArray ret = fft.spectrum(s);
    assertSame(s, ret);
    // Each element should be a complex ChuckArray
    for (int i = 0; i < 4; i++) {
      Object elem = s.getObject(i);
      assertTrue(elem instanceof ChuckArray, "s[" + i + "] should be ChuckArray");
      assertEquals("complex", ((ChuckArray) elem).vecTag);
    }
  }

  // ── IFFT.size() / transform() ─────────────────────────────────────────────

  @Test
  void testIfftSizeGetter() {
    IFFT ifft = new IFFT(512);
    assertEquals(512L, ifft.size());
  }

  @Test
  void testIfftTransformAcceptsComplexArray() {
    IFFT ifft = new IFFT(8);
    ChuckArray s = new ChuckArray("complex", 4);
    for (int i = 0; i < 4; i++) {
      ChuckArray elem = (ChuckArray) s.getObject(i);
      if (elem != null) {
        elem.setFloat(0, (float) Math.cos(i));
        elem.setFloat(1, (float) Math.sin(i));
      }
    }
    ChuckArray ret = ifft.transform(s);
    assertSame(s, ret); // returns the input array
  }

  // ── UAnaBlob API ──────────────────────────────────────────────────────────

  @Test
  void testBlobCvals() {
    FFT fft = new FFT(8);
    for (int i = 0; i < 16; i++) fft.tick(0.5f, i);
    UAnaBlob blob = fft.upchuck();
    ChuckArray cvals = blob.cvals();
    assertNotNull(cvals);
    assertEquals(4, cvals.size()); // size/2 bins
    Object elem = cvals.getObject(0);
    assertTrue(elem instanceof ChuckArray);
    assertEquals("complex", ((ChuckArray) elem).vecTag);
  }

  @Test
  void testBlobFvals() {
    FFT fft = new FFT(8);
    for (int i = 0; i < 16; i++) fft.tick(0.5f, i);
    UAnaBlob blob = fft.upchuck();
    ChuckArray fvals = blob.fvals();
    assertNotNull(fvals);
    assertEquals(4, fvals.size());
    // All magnitudes should be non-negative
    for (int i = 0; i < 4; i++) assertTrue(fvals.getFloat(i) >= 0.0);
  }

  @Test
  void testBlobCval() {
    FFT fft = new FFT(8);
    for (int i = 0; i < 16; i++) fft.tick(0.5f, i);
    UAnaBlob blob = fft.upchuck();
    ChuckArray c = blob.cval(0);
    assertNotNull(c);
    assertEquals("complex", c.vecTag);
    assertEquals(2, c.size());
  }

  @Test
  void testBlobFval() {
    FFT fft = new FFT(8);
    for (int i = 0; i < 16; i++) fft.tick(0.5f, i);
    UAnaBlob blob = fft.upchuck();
    double fval = blob.fval(0);
    assertTrue(fval >= 0.0);
  }

  // ── ChucK script integration ───────────────────────────────────────────────

  @Test
  void testChuckScript_fftSize() {
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    String code =
        "SinOsc s => FFT fft => blackhole;\n"
            + "1024 => fft.size;\n"
            + "<<< \"size:\", fft.size() >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(5);
    String o = out.toString();
    assertTrue(o.contains("size:") && o.contains("1024"), "Expected size:1024, got: " + o);
  }

  @Test
  void testChuckScript_fftUpchuckAndCval() {
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    // Mirror of fft.ck — call upchuck and examine cval(0)$polar
    String code =
        "SinOsc g => FFT fft => blackhole;\n"
            + "440.0 => g.freq;\n"
            + "8 => fft.size;\n"
            + "fft.upchuck();\n"
            + "<<< \"c:\", fft.cval(0)$polar >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(44100 / 2);
    assertTrue(out.toString().contains("c:"), "cval output missing: " + out);
  }

  @Test
  void testChuckScript_fftSpectrumArray() {
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    // Mirror of fft3.ck — fill complex array via fft.spectrum()
    String code =
        "SinOsc g => FFT fft => blackhole;\n"
            + "440.0 => g.freq;\n"
            + "8 => fft.size;\n"
            + "complex s[4];\n"
            + "fft.upchuck();\n"
            + "fft.spectrum(s);\n"
            + "<<< \"ok\" >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(44100 / 2);
    assertTrue(out.toString().contains("ok"), "spectrum() failed: " + out);
  }

  @Test
  void testChuckScript_blobCvals() {
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    // Mirror of fft2.ck — fft.upchuck().cvals() @=> c
    String code =
        "SinOsc g => FFT fft => blackhole;\n"
            + "440.0 => g.freq;\n"
            + "8 => fft.size;\n"
            + "complex c[];\n"
            + "fft.upchuck().cvals() @=> c;\n"
            + "<<< \"len:\", c.size() >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(44100 / 2);
    String o = out.toString();
    assertTrue(o.contains("len:"), "blob.cvals() chain failed: " + o);
  }

  @Test
  void testChuckScript_ifftTransform() {
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    // Mirror of ifft2.ck — manual FFT→spectrum→IFFT transform
    String code =
        "SinOsc g => FFT fft => blackhole;\n"
            + "IFFT ifft => blackhole;\n"
            + "440.0 => g.freq;\n"
            + "8 => fft.size;\n"
            + "complex s[4];\n"
            + "fft.upchuck();\n"
            + "fft.spectrum(s);\n"
            + "ifft.transform(s);\n"
            + "<<< \"ok\" >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(44100 / 2);
    assertTrue(out.toString().contains("ok"), "ifft.transform() failed: " + out);
  }
}
