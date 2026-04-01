package org.chuck.compiler;

import org.antlr.v4.runtime.*;
import org.chuck.audio.*;
import org.chuck.core.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for analysis / spatial audio features:
 * vec2/vec3/vec4 field accessors and arithmetic, complex/polar arithmetic,
 * operator overloading (comparison), ZCR, MFCC, SFM, Kurtosis,
 * and FM instrument variants.
 */
public class ChuckAnalysisAndSpatialTest {

    private List<String> runChuck(String source, int samples) throws InterruptedException {
        CharStream input = CharStreams.fromString(source);
        ChuckANTLRLexer lexer = new ChuckANTLRLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override public void syntaxError(Recognizer<?,?> r, Object sym, int line, int col, String msg, RecognitionException e) {
                throw new RuntimeException("Lexer " + line + ":" + col + " – " + msg);
            }
        });
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ChuckANTLRParser parser = new ChuckANTLRParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override public void syntaxError(Recognizer<?,?> r, Object sym, int line, int col, String msg, RecognitionException e) {
                throw new RuntimeException("Parser " + line + ":" + col + " – " + msg);
            }
        });
        ChuckASTVisitor visitor = new ChuckASTVisitor();
        @SuppressWarnings("unchecked")
        List<org.chuck.compiler.ChuckAST.Stmt> ast = (List<org.chuck.compiler.ChuckAST.Stmt>) visitor.visit(parser.program());
        ChuckEmitter emitter = new ChuckEmitter();
        ChuckCode code = emitter.emit(ast, "Test");
        ChuckVM vm = new ChuckVM(44100);
        List<String> output = Collections.synchronizedList(new ArrayList<>());
        vm.addPrintListener(output::add);
        vm.spork(new ChuckShred(code));
        Thread.sleep(150);
        vm.advanceTime(samples);
        return output;
    }

    // -------------------------------------------------------------------------
    // vec field accessors
    // -------------------------------------------------------------------------

    @Test
    public void testVec3FieldRead() throws InterruptedException {
        List<String> out = runChuck("""
            @(1.0, 2.0, 3.0) => vec3 v;
            <<< v.x, v.y, v.z >>>;
            """, 1);
        assertFalse(out.isEmpty());
        assertEquals("1.000000 2.000000 3.000000", out.get(0));
    }

    @Test
    public void testVec2FieldWrite() throws InterruptedException {
        List<String> out = runChuck("""
            vec2 v;
            5.0 => v.x;
            7.0 => v.y;
            <<< v.x, v.y >>>;
            """, 1);
        assertFalse(out.isEmpty());
        assertEquals("5.000000 7.000000", out.get(0));
    }

    @Test
    public void testComplexFieldAccess() throws InterruptedException {
        List<String> out = runChuck("""
            #(3.0, 4.0) => complex c;
            <<< c.re, c.im >>>;
            """, 1);
        assertFalse(out.isEmpty());
        assertEquals("3.000000 4.000000", out.get(0));
    }

    @Test
    public void testPolarFieldAccess() throws InterruptedException {
        List<String> out = runChuck("""
            %(2.0, 0.5) => polar p;
            <<< p.mag, p.phase >>>;
            """, 1);
        assertFalse(out.isEmpty());
        assertEquals("2.000000 0.500000", out.get(0));
    }

    // -------------------------------------------------------------------------
    // complex arithmetic
    // -------------------------------------------------------------------------

    @Test
    public void testComplexAdd() throws InterruptedException {
        List<String> out = runChuck("""
            #(1.0, 2.0) => complex a;
            #(3.0, 4.0) => complex b;
            a + b => complex c;
            <<< c.re, c.im >>>;
            """, 1);
        assertFalse(out.isEmpty());
        assertEquals("4.000000 6.000000", out.get(0));
    }

    @Test
    public void testComplexMul() throws InterruptedException {
        List<String> out = runChuck("""
            #(1.0, 2.0) => complex a;
            #(3.0, 4.0) => complex b;
            a * b => complex c;
            <<< c.re, c.im >>>;
            """, 1);
        // (1+2i)(3+4i) = 3-8 + (4+6)i = -5+10i
        assertFalse(out.isEmpty());
        assertEquals("-5.000000 10.000000", out.get(0));
    }

    // -------------------------------------------------------------------------
    // vec arithmetic
    // -------------------------------------------------------------------------

    @Test
    public void testVec3Add() throws InterruptedException {
        List<String> out = runChuck("""
            @(1.0, 2.0, 3.0) => vec3 u;
            @(4.0, 5.0, 6.0) => vec3 v;
            u + v => vec3 w;
            <<< w.x, w.y, w.z >>>;
            """, 1);
        assertFalse(out.isEmpty());
        assertEquals("5.000000 7.000000 9.000000", out.get(0));
    }

    @Test
    public void testVec3Dot() throws InterruptedException {
        List<String> out = runChuck("""
            @(1.0, 2.0, 3.0) => vec3 u;
            @(4.0, 5.0, 6.0) => vec3 v;
            u * v => float d;
            <<< d >>>;
            """, 1);
        // 1*4 + 2*5 + 3*6 = 4+10+18 = 32
        assertFalse(out.isEmpty());
        assertFalse(out.isEmpty(), "output was empty");
        assertTrue(out.get(0).contains("32.0") || out.get(0).contains("32"), "got: " + out.get(0));
    }

    // -------------------------------------------------------------------------
    // comparison operator overloading
    // -------------------------------------------------------------------------

    @Test
    public void testComparisonOpOverload() throws InterruptedException {
        List<String> out = runChuck("""
            class Box {
                float val;
                fun int @operator<(Box other) { return val < other.val; }
                fun int @operator==(Box other) { return val == other.val; }
            }
            Box a; 1.0 => a.val;
            Box b; 3.0 => b.val;
            <<< a.val, b.val >>>;
            <<< a < b >>>;
            <<< a == b >>>;
            """, 100);
        assertFalse(out.isEmpty());
        // ChucK-Java prints floats with 6 decimal places
        assertEquals("1.000000 3.000000", out.get(0));
        assertEquals("1", out.get(1));
        assertEquals("0", out.get(2));
    }

    // -------------------------------------------------------------------------
    // ZCR — direct Java unit test (no VM needed)
    // -------------------------------------------------------------------------

    @Test
    public void testZCRSine() {
        ZCR zcr = new ZCR(100);
        // Feed a 440 Hz sine at 44100 Hz — ~2 crossings per 100 samples → rate ≈ 0.02
        for (int i = 0; i < 100; i++) {
            float sample = (float) Math.sin(2 * Math.PI * 440.0 * i / 44100.0);
            zcr.addSample(sample);
        }
        float rate = zcr.getZCR();
        assertTrue(rate >= 0.0f && rate <= 1.0f);
        assertTrue(rate > 0.0f, "Expected non-zero ZCR for sine wave");
    }

    @Test
    public void testZCRSilence() {
        ZCR zcr = new ZCR(64);
        for (int i = 0; i < 64; i++) zcr.addSample(0.0f);
        assertEquals(0.0f, zcr.getZCR(), 0.001f);
    }

    @Test
    public void testZCRNoise() {
        ZCR zcr = new ZCR(1000);
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < 1000; i++) zcr.addSample(rng.nextFloat() * 2 - 1);
        // White noise crosses zero ~50% of samples
        float rate = zcr.getZCR();
        assertTrue(rate > 0.3f && rate < 0.7f, "Expected ZCR ≈ 0.5 for white noise, got " + rate);
    }

    // -------------------------------------------------------------------------
    // SFM — direct Java unit test
    // -------------------------------------------------------------------------

    @Test
    public void testSFMNoise() {
        SFM sfm = new SFM();
        // Flat spectrum (white noise) → SFM near 1.0
        float[] flatSpectrum = new float[256];
        for (int i = 0; i < flatSpectrum.length; i++) flatSpectrum[i] = 1.0f;
        sfm.computeFromSpectrum(flatSpectrum);
        assertEquals(1.0f, sfm.getResult(), 0.01f);
    }

    @Test
    public void testSFMTonal() {
        SFM sfm = new SFM();
        // Highly peaky spectrum (single tone) → SFM near 0
        float[] peakySpectrum = new float[256];
        peakySpectrum[10] = 1000.0f;  // one strong bin, rest near zero
        sfm.computeFromSpectrum(peakySpectrum);
        assertTrue(sfm.getResult() < 0.1f);
    }

    // -------------------------------------------------------------------------
    // Kurtosis — direct Java unit test
    // -------------------------------------------------------------------------

    @Test
    public void testKurtosisUniform() {
        Kurtosis kurt = new Kurtosis();
        // Uniform spectrum → kurtosis = 1.8 (known for uniform distribution)
        float[] flat = new float[100];
        for (int i = 0; i < flat.length; i++) flat[i] = 1.0f;
        kurt.computeFromSpectrum(flat);
        // All equal → variance = 0 → kurtosis = 0 in our implementation
        assertEquals(0.0f, kurt.getResult(), 0.001f);
    }

    @Test
    public void testKurtosisImpulsive() {
        Kurtosis kurt = new Kurtosis();
        // Highly impulsive: one large spike
        float[] impulse = new float[100];
        impulse[0] = 100.0f;
        kurt.computeFromSpectrum(impulse);
        // Large spike → high kurtosis
        assertTrue(kurt.getResult() > 5.0f);
    }

    // -------------------------------------------------------------------------
    // MFCC — direct Java unit test
    // -------------------------------------------------------------------------

    @Test
    public void testMFCCCoeffCount() {
        MFCC mfcc = new MFCC();
        // Feed a flat spectrum
        float[] flat = new float[257];
        for (int i = 0; i < flat.length; i++) flat[i] = 1.0f;
        mfcc.computeFromSpectrum(flat);
        float[] coeffs = mfcc.getCoefficients();
        assertEquals(13, coeffs.length);
    }

    @Test
    public void testMFCCNonZero() {
        MFCC mfcc = new MFCC();
        float[] spectrum = new float[257];
        for (int i = 0; i < spectrum.length; i++) spectrum[i] = (float)(i + 1) / 257.0f;
        mfcc.computeFromSpectrum(spectrum);
        float[] coeffs = mfcc.getCoefficients();
        // At least some coefficients should be non-zero
        float sum = 0;
        for (float c : coeffs) sum += Math.abs(c);
        assertTrue(sum > 0.0f);
    }

    // -------------------------------------------------------------------------
    // FM instruments — instantiation + tick smoke test
    // -------------------------------------------------------------------------

    @Test
    public void testFMInstrumentsInstantiate() {
        int sr = 44100;
        Wurley w = new Wurley(sr);    w.noteOn(0.8f); w.setFreq(440.0); w.tick(0);
        BeeThree b = new BeeThree(sr); b.noteOn(0.8f); b.setFreq(440.0); b.tick(0);
        HevyMetl h = new HevyMetl(sr); h.noteOn(0.8f); h.setFreq(440.0); h.tick(0);
        PercFlut p = new PercFlut(sr);  p.noteOn(0.8f); p.setFreq(440.0); p.tick(0);
        TubeBell t = new TubeBell(sr);  t.noteOn(0.8f); t.setFreq(440.0); t.tick(0);
        FMVoices f = new FMVoices(sr);  f.noteOn(0.8f); f.setFreq(440.0); f.tick(0);
        // All should produce finite audio
        assertTrue(Float.isFinite(w.getLastOut()));
        assertTrue(Float.isFinite(b.getLastOut()));
        assertTrue(Float.isFinite(h.getLastOut()));
        assertTrue(Float.isFinite(p.getLastOut()));
        assertTrue(Float.isFinite(t.getLastOut()));
        assertTrue(Float.isFinite(f.getLastOut()));
    }

    @Test
    public void testFMNoteOnOff() {
        Wurley w = new Wurley(44100);
        w.setFreq(261.63);
        w.noteOn(1.0f);
        float onPeak = 0;
        for (int i = 0; i < 400; i++) {
            w.tick(i);
            float out = Math.abs(w.getLastOut());
            if (out > onPeak) onPeak = out;
        }
        w.noteOff(0.5f);
        // Release time is 0.08s = 3528 samples. Tick 4000 samples to ensure full decay
        for (int i = 400; i < 4400; i++) w.tick(i);
        float offPeak = 0;
        for (int i = 4400; i < 4800; i++) {
            w.tick(i);
            float out = Math.abs(w.getLastOut());
            if (out > offPeak) offPeak = out;
        }
        assertTrue(offPeak < onPeak);
        assertTrue(offPeak < 0.01f); // Should be very close to completely silent
    }

    @Test
    public void testFMInstantiateViaVM() throws InterruptedException {
        List<String> out = runChuck("""
            Wurley w => dac;
            220.0 => w.freq;
            w.noteOn(0.8);
            1::samp => now;
            w.noteOff(0.5);
            <<< "ok" >>>;
            """, 10);
        assertFalse(out.isEmpty());
        assertEquals("ok", out.get(0));
    }
}
