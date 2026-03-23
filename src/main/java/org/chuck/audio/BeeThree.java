package org.chuck.audio;

/**
 * BeeThree — Hammond-style B3 organ using 3-operator FM + additive mixing.
 * Produces characteristic organ harmonics via additive FM.
 */
public class BeeThree extends ChuckUGen {
    // Three operators for organ harmonics
    private final SinOsc op1;   // fundamental
    private final SinOsc op2;   // second harmonic
    private final SinOsc op3;   // sub-harmonic
    private final Adsr env;
    private double baseFreq = 440.0;
    @SuppressWarnings("unused")
    private final float sampleRate;

    public BeeThree(float sampleRate) {
        this.sampleRate = sampleRate;
        this.op1 = new SinOsc(sampleRate);
        this.op2 = new SinOsc(sampleRate);
        this.op3 = new SinOsc(sampleRate);
        this.env = new Adsr(sampleRate);

        // Organ-style: fast attack, sustained, fast release
        env.set(0.0001f, 0.001f, 0.9f, 0.01f);
    }

    public void setFreq(double freq) {
        this.baseFreq = freq;
        op1.setFreq(freq);          // fundamental (8' drawbar)
        op2.setFreq(freq * 2.0);    // octave (4' drawbar)
        op3.setFreq(freq * 0.5);    // sub-octave (16' drawbar)
    }

    public void noteOn(float velocity) {
        env.keyOn();
    }

    public void noteOff(float velocity) {
        env.keyOff();
    }

    @Override
    protected float compute(float input, long systemTime) {
        float e = env.tick(systemTime);
        float s1 = op1.tick(systemTime);
        float s2 = op2.tick(systemTime);
        float s3 = op3.tick(systemTime);
        // Additive mix: fundamental strongest, sub and second at lower levels
        float out = (s1 * 0.6f + s2 * 0.25f + s3 * 0.15f) * e * gain;
        return out;
    }
}
