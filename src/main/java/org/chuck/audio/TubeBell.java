package org.chuck.audio;

/**
 * TubeBell — Tubular bell synthesized via 4-operator FM.
 * Uses inharmonic modulator ratios (√2 ≈ 1.414) for bell-like partials,
 * with a long exponential decay envelope.
 */
public class TubeBell extends ChuckUGen {
    private final SinOsc carrier1;
    private final SinOsc carrier2;
    private final SinOsc mod1;
    private final SinOsc mod2;
    private final Adsr env1;
    private final Adsr env2;
    private double baseFreq = 440.0;
    @SuppressWarnings("unused")
    private final float sampleRate;

    // √2 ratio produces characteristic bell inharmonicity
    private static final double SQRT2 = Math.sqrt(2.0);

    public TubeBell(float sampleRate) {
        this.sampleRate = sampleRate;
        this.carrier1 = new SinOsc(sampleRate);
        this.carrier2 = new SinOsc(sampleRate);
        this.mod1 = new SinOsc(sampleRate);
        this.mod2 = new SinOsc(sampleRate);
        this.env1 = new Adsr(sampleRate);
        this.env2 = new Adsr(sampleRate);

        mod1.chuckTo(carrier1);
        mod2.chuckTo(carrier2);
        carrier1.setSync(2);
        carrier2.setSync(2);

        // Bell: fast attack, long decay, no sustain
        env1.set(0.001f, 3.0f, 0.0f, 0.2f);
        env2.set(0.001f, 1.5f, 0.0f, 0.1f);
    }

    public void setFreq(double freq) {
        this.baseFreq = freq;
        carrier1.setFreq(freq);
        carrier2.setFreq(freq * SQRT2);  // inharmonic second partial
        mod1.setFreq(freq * SQRT2);      // inharmonic modulator
        mod2.setFreq(freq * 1.0);        // fundamental modulator
    }

    public void noteOn(float velocity) {
        env1.keyOn();
        env2.keyOn();
    }

    public void noteOff(float velocity) {
        env1.keyOff();
        env2.keyOff();
    }

    @Override
    protected float compute(float input, long systemTime) {
        float e1 = env1.tick(systemTime);
        float e2 = env2.tick(systemTime);
        float c1 = carrier1.tick(systemTime);
        float c2 = carrier2.tick(systemTime);
        @SuppressWarnings("unused")
        float m1 = mod1.tick(systemTime);
        @SuppressWarnings("unused")
        float m2 = mod2.tick(systemTime);
        float out = (c1 * e1 + c2 * e2 * 0.5f) * gain;
        return out;
    }
}
