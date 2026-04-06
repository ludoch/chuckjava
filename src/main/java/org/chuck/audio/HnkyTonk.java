package org.chuck.audio;

/**
 * HnkyTonk — FM honky-tonk piano.
 * Produces the classic out-of-tune piano sound by detuning two sets of
 * FM operator pairs a few cents apart.
 */
public class HnkyTonk extends ChuckUGen {
    private final SinOsc modA, carA; // in-tune pair
    private final SinOsc modB, carB; // detuned pair
    private final Adsr env;
    private double freq = 220.0;
    private double modIndex = 2.5;
    private static final double DETUNE = 1.003; // ~5 cents sharp
    private final float sampleRate;

    public HnkyTonk(float sr) {
        this.sampleRate = sr;
        modA = new SinOsc(sr); carA = new SinOsc(sr);
        modB = new SinOsc(sr); carB = new SinOsc(sr);
        env  = new Adsr(sr);
        env.set(0.001f, 0.05f, 0.7f, 0.1f);
        setFreq(220.0);
    }

    public void setFreq(double f) {
        freq = f;
        modA.setFreq(f);      carA.setFreq(f);
        modB.setFreq(f * DETUNE); carB.setFreq(f * DETUNE);
    }

    public void noteOn(float v)  { env.keyOn(); }
    public void noteOff(float v) { env.keyOff(); }

    @Override
    protected float compute(float input, long t) {
        float e = env.tick(t);
        double modSigA = modA.tick(t) * modIndex;
        carA.setFreq(freq + modSigA * freq);
        double modSigB = modB.tick(t) * modIndex;
        carB.setFreq(freq * DETUNE + modSigB * freq * DETUNE);
        float out = (carA.tick(t) + carB.tick(t)) * 0.5f * e * gain;
        lastOut = out;
        return out;
    }
}
