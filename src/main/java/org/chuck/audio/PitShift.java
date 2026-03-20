package org.chuck.audio;

/**
 * PitShift: Time-domain pitch shifter using two cross-fading delay lines.
 */
public class PitShift extends ChuckUGen {
    private final DelayL[] delays = new DelayL[2];
    private double shift = 1.0;
    private double phase = 0.0;
    private final int windowSize = 1024;
    private final float sampleRate;

    public PitShift(float sampleRate) {
        super();
        this.sampleRate = sampleRate;
        delays[0] = new DelayL(windowSize * 2, sampleRate);
        delays[1] = new DelayL(windowSize * 2, sampleRate);
        delays[0].setDelay(0);
        delays[1].setDelay(windowSize);
    }

    public PitShift() {
        this(44100.0f);
    }

    @Override
    protected float compute(float input, long systemTime) {
        // Simple sawtooth phase for pitch shifting
        // (shift - 1.0) is the rate of change of delay
        double rate = (shift - 1.0) / windowSize;
        phase += rate * (sampleRate / 44100.0); // normalize? no, rate is per sample
        
        // Wait, phase increment should be (1.0 - shift) / windowSize in samples per sample
        double phaseInc = (1.0 - shift); 
        phase += phaseInc;
        if (phase < 0) phase += windowSize;
        if (phase >= windowSize) phase -= windowSize;

        double p1 = phase;
        double p2 = (phase + windowSize / 2.0) % windowSize;

        delays[0].setDelay(p1);
        delays[1].setDelay(p2);

        float out1 = delays[0].tick(input, systemTime);
        float out2 = delays[1].tick(input, systemTime);

        // Triangular cross-fade window
        double w1 = 1.0 - Math.abs((p1 - windowSize / 2.0) / (windowSize / 2.0));
        double w2 = 1.0 - Math.abs((p2 - windowSize / 2.0) / (windowSize / 2.0));

        return (float) (out1 * w1 + out2 * w2);
    }

    public void shift(double s) { this.shift = s; }
    public double shift() { return shift; }
}
