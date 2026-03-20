package org.chuck.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

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
        
        float firstLevel = adsr.compute(1.0f, -1);
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
        float[] samples = { 0.0f, 0.2f, 0.4f, 0.6f, 0.8f, 1.0f };
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
}
