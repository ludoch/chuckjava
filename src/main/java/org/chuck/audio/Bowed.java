package org.chuck.audio;

/**
 * A bowed string physical model.
 */
public class Bowed extends ChuckUGen {
    private final DelayL neckDelay;
    private final DelayL bridgeDelay;
    private final BowTable bowTable;
    private final OnePole filter;
    
    @SuppressWarnings("unused")
    private float bowPressure = 0.0f;
    private float bowVelocity = 0.0f;
    private float vibratoFreq = 6.125f;
    private float vibratoGain = 0.0f;
    @SuppressWarnings("unused")
    private double freq = 220.0;
    private float sampleRate;
    
    private double phase = 0.0;

    public Bowed(float sampleRate) {
        this.sampleRate = sampleRate;
        int maxDelay = (int) (sampleRate / 20.0); // Down to 20Hz
        neckDelay = new DelayL(maxDelay);
        bridgeDelay = new DelayL(maxDelay);
        bowTable = new BowTable();
        filter = new OnePole();
        filter.setPole(0.95f);
        
        setFreq(220.0);
    }

    public void setFreq(double f) {
        this.freq = f;
        double totalDelay = sampleRate / f;
        neckDelay.setDelay(totalDelay * 0.75); // Nut to bow
        bridgeDelay.setDelay(totalDelay * 0.25); // Bow to bridge
    }

    public void bowPressure(float p) { this.bowPressure = p; }
    public void bowVelocity(float v) { this.bowVelocity = v; }
    public void vibratoFreq(float f) { this.vibratoFreq = f; }
    public void vibratoGain(float g) { this.vibratoGain = g; }

    public void noteOn(float velocity) {
        bowVelocity = 0.05f + (velocity * 0.2f);
        bowPressure = 0.1f + (velocity * 0.1f);
    }

    public void noteOff(float velocity) {
        bowVelocity = 0.0f;
    }

    @Override
    protected float compute(float input, long systemTime) {
        // Simple vibrato
        @SuppressWarnings("unused")
        double vibrato = Math.sin(phase) * vibratoGain;
        phase += 2.0 * Math.PI * vibratoFreq / sampleRate;
        if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI;
        
        // This is a simplified waveguide bow model
        float bridgeReflection = -bridgeDelay.tick(0.0f, systemTime); // Use tick(0) to pull last out
        float neckReflection = -filter.tick(neckDelay.tick(0.0f, systemTime), systemTime);
        
        float bowDiff = bowVelocity - (bridgeReflection + neckReflection);
        float newVel = bowDiff * bowTable.lookup(bowDiff);
        
        bridgeDelay.tick(neckReflection + newVel, systemTime);
        neckDelay.tick(bridgeReflection + newVel, systemTime);
        
        lastOut = bridgeReflection;
        return lastOut;
    }
}
