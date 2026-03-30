package org.chuck.audio;

/**
 * Band-Limited Impulse Train (BLIT) oscillator.
 *
 * Implements the Stilson-Smith algorithm:
 *   output = sin(M * phase) / (M * sin(phase))
 * where M = 2*nHarmonics+1 is chosen so harmonics stay below Nyquist.
 *
 * This generates a train of band-limited impulses at the set frequency.
 * By default nHarmonics = 0, meaning all harmonics up to sr/2 are included.
 */
public class Blit extends ChuckUGen {
    private double phase      = 0.0;
    private double rate       = 0.0;   // phase increment per sample
    private double period     = 0.0;   // period in samples
    private int    m          = 1;     // number of harmonics (2*N+1)
    private int    nHarmonics = 0;     // 0 = auto (all harmonics)
    private double frequency  = 220.0;

    private final float sampleRate;
    private static final double EPSILON = 1e-9;

    public Blit(float sampleRate) {
        this.sampleRate = sampleRate;
        setFrequency(220.0);
    }

    public void setFrequency(double freq) {
        if (freq <= 0) freq = 1.0;
        frequency = freq;
        period    = sampleRate / freq;
        rate      = Math.PI / period;
        updateHarmonics();
    }

    /** Set number of harmonics explicitly (0 = auto, use all up to Nyquist). */
    public void setHarmonics(int n) {
        nHarmonics = n;
        updateHarmonics();
    }

    private void updateHarmonics() {
        if (nHarmonics <= 0)
            m = 2 * (int) Math.floor(0.5 * period) + 1;
        else
            m = 2 * nHarmonics + 1;
    }

    /** Reset phase accumulator. */
    public void reset() { phase = 0.0; }

    // ChucK-style accessors
    public double freq(double f)  { setFrequency(f); return f; }
    public double freq()          { return frequency; }
    public int    harmonics(int n){ setHarmonics(n); return n; }
    public int    harmonics()     { return nHarmonics; }

    @Override
    protected float compute(float input, long systemTime) {
        double denominator = Math.sin(phase);
        double out;
        if (Math.abs(denominator) <= EPSILON) {
            out = 1.0; // limit value (L'Hôpital: sin(m*x)/(m*sin(x)) → 1 as x→0)
        } else {
            out = Math.sin(m * phase) / (m * denominator);
        }
        phase += rate;
        if (phase >= Math.PI) phase -= Math.PI;
        return (float) out;
    }
}
