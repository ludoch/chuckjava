package org.chuck.audio;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.audio.filter.OnePole;
import org.chuck.audio.filter.OneZero;
import org.chuck.audio.filter.PoleZero;
import org.chuck.audio.filter.TwoPole;
import org.chuck.audio.filter.TwoZero;
import org.chuck.audio.fx.Delay;
import org.chuck.audio.fx.DelayA;
import org.chuck.audio.osc.Blit;
import org.chuck.audio.osc.CNoise;
import org.chuck.audio.osc.SineWave;
import org.chuck.audio.util.Adsr;
import org.chuck.audio.util.Gain;
import org.chuck.audio.util.Impulse;
import org.chuck.audio.util.Pan2;
import org.chuck.audio.util.SndBuf;
import org.chuck.audio.util.Step;
import org.junit.jupiter.api.Test;

public class ChuckUGenTest {

  @Test
  public void testUGenGraph() {
    float sr = 44100.0f;
    SineWave sine = new SineWave(sr);
    sine.setFrequency(440.0f);

    Gain gain = new Gain();
    gain.setGain(0.5f);

    // SineWave => Gain
    sine.chuckTo(gain);

    // Tick the sine wave (source) first
    sine.tick(0);
    float outSine = sine.getLastOut();

    // Tick the gain (downstream)
    gain.tick(0);
    float outGain = gain.getLastOut();

    assertEquals(outSine * 0.5f, outGain, 1e-6);
  }

  @Test
  public void testVectorGain() {
    Gain gain = new Gain();
    gain.setGain(0.5f);

    float[] buffer = new float[256];
    for (int i = 0; i < buffer.length; i++) {
      buffer[i] = 1.0f;
    }

    // Process the block
    gain.tick(buffer);

    for (float f : buffer) {
      assertEquals(0.5f, f, 1e-6);
    }
  }

  @Test
  public void testAdsr() {
    float sr = 44100.0f;
    Adsr adsr = new Adsr(sr);

    // 1.0 => adsr (simulated)
    adsr.keyOn();

    // Initial state should be silent/0.0 until first tick
    // But in my Adsr::compute, it increments on the first tick.

    float firstLevel = adsr.tick(1.0f, -1);
    assertTrue(firstLevel > 0.0f && firstLevel < 1.0f);

    // Fast forward to steady state (sustain)
    for (int i = 0; i < 5000; i++) {
      adsr.tick();
    }

    assertEquals(0.5f, adsr.getCurrentLevel(), 1e-4);

    adsr.keyOff();

    // Release
    for (int i = 0; i < 5000; i++) {
      adsr.tick();
    }
    assertEquals(0.0f, adsr.getCurrentLevel(), 1e-4);
  }

  @Test
  public void testSndBuf() {
    SndBuf buf = new SndBuf();
    float[] samples = {0.0f, 0.2f, 0.4f, 0.6f, 0.8f, 1.0f};
    buf.setSamples(samples);
    buf.setRate(0.5); // 0.5 samples per tick

    // Tick 1: pos=0, s0=0.0
    assertEquals(0.0f, buf.tick(), 1e-6);
    // Tick 2: pos=0.5, s0=0.0, s1=0.2, frac=0.5
    assertEquals(0.1f, buf.tick(), 1e-6);
    // Tick 3: pos=1.0, s1=0.2
    assertEquals(0.2f, buf.tick(), 1e-6);
  }

  @Test
  public void testStepAndImpulse() {
    Step step = new Step();
    step.setNext(0.5f);
    assertEquals(0.5f, step.tick(), 1e-6);
    assertEquals(0.5f, step.tick(), 1e-6);

    Impulse imp = new Impulse();
    imp.setNext(1.0f);
    assertEquals(1.0f, imp.tick(), 1e-6);
    assertEquals(0.0f, imp.tick(), 1e-6);
  }

  @Test
  public void testDelay() {
    Delay delay = new Delay(10);
    delay.setDelay(2); // 2 sample delay

    Step step = new Step();
    step.setNext(1.0f);
    step.chuckTo(delay);

    // Tick 0: delay buffer empty, outputs 0
    step.tick();
    assertEquals(0.0f, delay.tick(), 1e-6);

    // Tick 1: delay buffer still empty at read pos, outputs 0
    step.tick();
    assertEquals(0.0f, delay.tick(), 1e-6);

    // Tick 2: delay buffer now has 1.0 from Tick 0
    step.tick();
    assertEquals(1.0f, delay.tick(), 1e-6);
  }

  @Test
  public void testPan2() {
    Pan2 pan = new Pan2();
    pan.setPanType(0); // linear

    Step step = new Step();
    step.setNext(1.0f);
    step.chuckTo(pan);

    // Center pan
    pan.setPan(0.0f);
    step.tick();
    pan.tick();
    assertEquals(0.5f, pan.getLastOutLeft(), 1e-6);
    assertEquals(0.5f, pan.getLastOutRight(), 1e-6);

    // Hard left
    pan.setPan(-1.0f);
    step.tick();
    pan.tick();
    assertEquals(1.0f, pan.getLastOutLeft(), 1e-6);
    assertEquals(0.0f, pan.getLastOutRight(), 1e-6);
  }

  @Test
  public void testFoundationalFilters() {
    OnePole op = new OnePole();
    op.setPole(0.9f);
    assertTrue(op.tick(1.0f) < 1.0f);

    OneZero oz = new OneZero();
    oz.setZero(0.5f);
    assertEquals(0.6666667f, oz.tick(1.0f), 1e-6);
  }

  @Test
  public void testTwoPole() {
    float sr = 44100.0f;
    TwoPole tp = new TwoPole(sr);
    tp.setResonance(440.0, 0.9, false);

    Step step = new Step();
    step.setNext(1.0f);
    step.chuckTo(tp);

    // First tick: b0 * input (no history yet)
    step.tick();
    float out0 = tp.tick();
    assertEquals(1.0f, out0, 1e-5f); // b0=1, a1/a2 have no history yet

    // Second tick: should be non-trivial (pole feedback kicks in)
    step.tick();
    float out1 = tp.tick();
    assertNotEquals(0.0f, out1);

    // With high pole radius the filter should ring — output should grow or stay non-zero
    assertTrue(Math.abs(out1) > 0.0f);
  }

  @Test
  public void testTwoZero() {
    float sr = 44100.0f;
    TwoZero tz = new TwoZero(sr);
    tz.setNotch(1000.0, 0.5);

    // DC input (frequency = 0) should NOT be fully cancelled
    float dcOut = 0.0f;
    for (int i = 0; i < 10; i++) dcOut = tz.tick(1.0f);
    // Steady-state with DC: output = (b0 + b1 + b2) * 1.0
    // Normalization ensures unity at DC for the non-notch frequency
    assertTrue(Math.abs(dcOut) > 0.0f);

    // Impulse response: finite (FIR filter settles in 2 samples)
    TwoZero tz2 = new TwoZero(sr);
    tz2.setNotch(5512.5, 0.99); // notch at sr/8
    float imp0 = tz2.tick(1.0f);
    float imp1 = tz2.tick(0.0f);
    float imp2 = tz2.tick(0.0f);
    float imp3 = tz2.tick(0.0f); // should be 0 — FIR length = 3
    assertEquals(0.0f, imp3, 1e-6f);
    // sum of coefficients (impulse response sum) should be near 0 at notch
    // but total gain at DC/Nyquist should not be 0
    assertNotEquals(0.0f, imp0);
    assertNotEquals(imp0, imp1); // b0 != b1 for non-trivial notch
  }

  @Test
  public void testPoleZero() {
    // Test allpass mode: unity gain at all frequencies.
    // A first-order IIR allpass distributes impulse energy over many samples;
    // accumulate 100 samples and verify total energy ≈ 1.
    PoleZero pz = new PoleZero();
    pz.setAllpass(0.5);

    double energy = 0.0;
    energy += Math.pow(pz.tick(1.0f), 2);
    for (int i = 1; i < 100; i++) energy += Math.pow(pz.tick(0.0f), 2);
    assertEquals(1.0, energy, 0.01);

    // Test DC blocker mode
    PoleZero dcb = new PoleZero();
    dcb.setBlockZero(0.99);
    // Feed DC for many samples; output should converge to ~0
    float dcOut = 0.0f;
    for (int i = 0; i < 5000; i++) dcOut = dcb.tick(1.0f);
    assertEquals(0.0f, dcOut, 0.01f);
  }

  @Test
  public void testDelayA() {
    // DelayA should delay by approximately the requested number of samples
    DelayA da = new DelayA(16);
    da.setDelay(4.0);

    // Feed an impulse
    float[] out = new float[8];
    out[0] = da.tick(1.0f);
    for (int i = 1; i < 8; i++) out[i] = da.tick(0.0f);

    // The impulse should appear at approximately sample 4 (allpass shifts by ~delay)
    float maxVal = 0;
    int maxIdx = 0;
    for (int i = 0; i < 8; i++) {
      if (Math.abs(out[i]) > maxVal) {
        maxVal = Math.abs(out[i]);
        maxIdx = i;
      }
    }
    // Peak of impulse response should be at or near the set delay
    assertTrue(maxIdx >= 3 && maxIdx <= 5, "Impulse peak at " + maxIdx + ", expected ~4");
    assertTrue(maxVal > 0.5f, "Impulse peak too small: " + maxVal);
  }

  @Test
  public void testBlit() {
    float sr = 44100.0f;
    Blit blit = new Blit(sr);
    blit.setFrequency(440.0);

    // BLIT output should oscillate; check that we get both positive and negative values
    boolean seenPositive = false, seenNegative = false;
    for (int i = 0; i < 200; i++) {
      float s = blit.tick();
      if (s > 0.1f) seenPositive = true;
      if (s < -0.1f) seenNegative = true;
    }
    assertTrue(seenPositive, "BLIT should produce positive samples");
    assertTrue(seenNegative, "BLIT should produce negative samples");

    // Output should be bounded
    Blit blit2 = new Blit(sr);
    blit2.setFrequency(220.0);
    for (int i = 0; i < 500; i++) {
      float s = blit2.tick();
      assertTrue(Math.abs(s) <= 1.5f, "BLIT sample out of range: " + s);
    }
  }

  @Test
  public void testCNoise() {
    CNoise cn = new CNoise();

    // White mode
    cn.mode("white");
    boolean pos = false, neg = false;
    for (int i = 0; i < 1000; i++) {
      float s = cn.tick();
      if (s > 0) pos = true;
      if (s < 0) neg = true;
    }
    assertTrue(pos && neg, "White noise should have both polarities");

    // Pink mode — output should be in [-1, 1] range
    cn.mode("pink");
    for (int i = 0; i < 1000; i++) {
      float s = cn.tick();
      assertTrue(Math.abs(s) <= 1.1f, "Pink noise sample out of range: " + s);
    }

    // Brown mode — should be smooth (adjacent samples differ < 0.1)
    cn.mode("brown");
    float prev = cn.tick();
    for (int i = 0; i < 500; i++) {
      float s = cn.tick();
      assertTrue(Math.abs(s - prev) < 0.1f, "Brown noise too jumpy at sample " + i);
      prev = s;
    }

    // mode() getter
    assertEquals("brown", cn.mode());
  }
}
