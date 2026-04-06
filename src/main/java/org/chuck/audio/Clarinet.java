package org.chuck.audio;

/**
 * A clarinet physical model.
 */
public class Clarinet extends ChuckUGen {
    private final DelayL delayLine;
    private final ReedTable reedTable;
    private final OneZero filter;
    private final Envelope envelope;
    private final Noise noise;
    private final SinOsc vibrato;
    
    private float noiseGain = 0.2f;
    private float vibratoGain = 0.1f;
    private float outputGain = 1.0f;
    private final float sampleRate;

    public Clarinet(float lowestFrequency, float sampleRate) {
        this.sampleRate = sampleRate;
        int length = (int) (sampleRate / lowestFrequency + 1);
        this.delayLine = new DelayL(length);
        this.reedTable = new ReedTable();
        this.filter = new OneZero();
        this.envelope = new Envelope(sampleRate);
        this.noise = new Noise();
        this.vibrato = new SinOsc(sampleRate);
        this.vibrato.setFreq(5.735);
        
        filter.setB0(0.5f);
        filter.setB1(0.5f);
    }

    public void setFreq(double frequency) {
        double delay = sampleRate / frequency - 1.0;
        delayLine.setDelay(delay);
    }

    public void noteOn(float velocity) {
        envelope.setTarget(velocity);
        envelope.keyOn();
    }

    public void noteOff(float velocity) {
        envelope.keyOff();
    }

    @Override
    protected float compute(float input, long systemTime) {
        // Advance envelope ramp state; read value directly (envelope has no audio source,
        // so tick() returns 0 — we need getValue() to get the actual ramp level).
        envelope.tick(systemTime);
        float breathPressure = envelope.getValue();
        breathPressure += breathPressure * noiseGain * noise.tick(systemTime);
        breathPressure += breathPressure * vibratoGain * vibrato.tick(systemTime);

        float pressureDiff = -0.95f * filter.tick(delayLine.getLastOut(), systemTime);
        pressureDiff = pressureDiff - breathPressure;

        float out = delayLine.tick(breathPressure + pressureDiff * reedTable.tick(pressureDiff), systemTime);
        return out * outputGain;
    }
}
