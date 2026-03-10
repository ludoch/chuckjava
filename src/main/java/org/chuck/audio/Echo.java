package org.chuck.audio;

/**
 * An echo effect with feedback.
 */
public class Echo extends ChuckUGen {
    private final Delay delayLine;
    private float mix = 0.5f;
    private float lastWet = 0.0f;

    public Echo(int maxDelaySamples) {
        this.delayLine = new Delay(maxDelaySamples);
    }

    public void setDelay(int samples) {
        delayLine.setDelay(samples);
    }

    public void setMix(float mix) {
        this.mix = mix;
    }

    @Override
    protected float compute(float input) {
        // Feedback loop: input + (last wet output * gain)
        // Note: ChucK's Echo often uses the internal Gain of the UGen for feedback
        float dry = input;
        float wet = delayLine.tick(input + lastWet * gain);
        lastWet = wet;
        
        return dry * (1.0f - mix) + wet * mix;
    }
}
