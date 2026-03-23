package org.chuck.audio;

/**
 * FMVoices — Singing voice synthesis using 4-operator FM.
 * Formant-like filtering via multiple FM operators tuned to vocal tract
 * resonances. Slow vibrato applied to carrier for natural voice.
 */
public class FMVoices extends ChuckUGen {
    private final SinOsc carrier;
    private final SinOsc vibrato;
    private final SinOsc mod1;   // first formant modulator
    private final SinOsc mod2;   // second formant modulator
    private final Adsr env;
    private final Adsr modEnv;
    private double baseFreq = 440.0;
    private float vibratoDepth = 0.01f;
    @SuppressWarnings("unused")
    private final float sampleRate;

    public FMVoices(float sampleRate) {
        this.sampleRate = sampleRate;
        this.carrier = new SinOsc(sampleRate);
        this.vibrato = new SinOsc(sampleRate);
        this.mod1 = new SinOsc(sampleRate);
        this.mod2 = new SinOsc(sampleRate);
        this.env = new Adsr(sampleRate);
        this.modEnv = new Adsr(sampleRate);

        // Vibrato at ~6 Hz, typical for vocal vibrato
        vibrato.setFreq(6.0);
        // FM: mod1 and mod2 modulate carrier
        mod1.chuckTo(carrier);
        carrier.setSync(2);

        // Vocal: medium attack, sustained, natural release
        env.set(0.05f, 0.1f, 0.7f, 0.15f);
        modEnv.set(0.05f, 0.2f, 0.5f, 0.1f);
    }

    public void setFreq(double freq) {
        this.baseFreq = freq;
        carrier.setFreq(freq);
        mod1.setFreq(freq * 1.0);    // fundamental reinforcement
        mod2.setFreq(freq * 2.0);    // second formant harmonic
    }

    public void setVibratoDepth(double depth) {
        this.vibratoDepth = (float) depth;
    }

    public void noteOn(float velocity) {
        env.keyOn();
        modEnv.keyOn();
    }

    public void noteOff(float velocity) {
        env.keyOff();
        modEnv.keyOff();
    }

    @Override
    protected float compute(float input, long systemTime) {
        // Vibrato: slightly modulate carrier frequency
        float vib = vibrato.tick(systemTime) * vibratoDepth;
        carrier.setFreq(baseFreq * (1.0 + vib));

        float e = env.tick(systemTime);
        @SuppressWarnings("unused")
        float me = modEnv.tick(systemTime);
        float s1 = mod1.tick(systemTime);
        float s2 = mod2.tick(systemTime);
        float car = carrier.tick(systemTime);

        // Mix carrier with formant modulators for vocal quality
        float out = (car + s1 * 0.15f + s2 * 0.1f) * e * gain;
        return out;
    }
}
