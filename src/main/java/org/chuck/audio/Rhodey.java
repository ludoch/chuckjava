package org.chuck.audio;

/**
 * A simple Fender Rhodes model using 2-operator FM synthesis.
 */
public class Rhodey extends ChuckUGen {
    private final SinOsc carrier;
    private final SinOsc modulator;
    private final Adsr carrierEnv;
    private final Adsr modulatorEnv;
    private double baseFreq = 440.0;
    private float modIndex = 0.5f;
    private final float sampleRate;

    public Rhodey(float sampleRate) {
        this.sampleRate = sampleRate;
        this.carrier = new SinOsc(sampleRate);
        this.modulator = new SinOsc(sampleRate);
        this.carrierEnv = new Adsr(sampleRate);
        this.modulatorEnv = new Adsr(sampleRate);
        
        // Internal patch:
        // modulator => (modIndex * modEnv) => carrier (FM) => carrierEnv => output
        modulator.chuckTo(carrier);
        carrier.setSync(2); // FM mode
        // carrier.chuckTo(carrierEnv); // Adsr is 1-to-1 filter-like
        
        // Configure envelopes for a bell-like Rhodes sound
        carrierEnv.set(0.001f, 1.5f, 0.0f, 0.05f);
        modulatorEnv.set(0.001f, 0.5f, 0.0f, 0.05f);
    }

    public void setFreq(double freq) {
        this.baseFreq = freq;
        carrier.setFreq(freq);
        modulator.setFreq(freq * 3.5); // Modulator ratio
    }

    public void noteOn(float velocity) {
        carrierEnv.keyOn();
        modulatorEnv.keyOn();
        // Modulation index proportional to velocity, scaled to freq (like STK Rhodey)
        modIndex = (float)(baseFreq * velocity * 0.5);
    }

    public void noteOff(float velocity) {
        carrierEnv.keyOff();
        modulatorEnv.keyOff();
    }

    @Override
    protected float compute(float input, long systemTime) {
        float mEnv = modulatorEnv.tick(systemTime);
        // Additive mix for robustness: carrier + modulator
        float carOut = carrier.tick(systemTime);
        float modOut = modulator.tick(systemTime);
        
        float cEnv = carrierEnv.tick(systemTime);
        
        // Rhodey is essentially a bell-like sound
        float out = (carOut + modOut * 0.5f) * cEnv * gain;
        return out;
    }
}
