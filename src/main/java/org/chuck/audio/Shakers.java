package org.chuck.audio;

import java.util.Random;

/**
 * Shakers: STK stochastic percussion model.
 * Simulates maracas, cabasa, sekere, etc.
 */
public class Shakers extends ChuckUGen {
    private final Random random = new Random();
    private int type = 0;
    private float energy = 0.0f;
    private float shakeLevel = 0.0f;
    private int numObjects = 20;
    private float systemDecay = 0.999f;
    
    private final ResonZ filter;
    private final float sampleRate;

    public Shakers(float sampleRate) {
        this.sampleRate = sampleRate;
        filter = new ResonZ(sampleRate);
        filter.setFreq(3200.0f);
        filter.setQ(0.2f);
    }

    public void preset(int t) { this.type = t; }
    public void objects(int n) { this.numObjects = n; }
    public void energy(float e) { this.energy = e; }

    public void noteOn(float velocity) {
        shakeLevel += velocity * 0.5f;
    }

    @Override
    protected float compute(float input, long systemTime) {
        // Stochastic "collision" probability
        float coll = 0.0f;
        if (random.nextFloat() < (shakeLevel * numObjects / 1000.0f)) {
            coll = random.nextFloat() * 2.0f - 1.0f;
        }
        
        shakeLevel *= systemDecay;
        
        lastOut = filter.tick(coll, systemTime);
        return lastOut;
    }
}
