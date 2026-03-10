package org.chuck.audio;

/**
 * Audio Device Controller (input) — the ADC UGen.
 *
 * Sits at the root of the capture side of the audio graph. The audio engine
 * writes one sample-pair per sample period via {@link #setInputSample}; the
 * UGen graph then pulls that value through whatever is chucked to 'adc'.
 *
 * Stereo: channel 0 = left, channel 1 = right.
 * {@link #compute} returns the average of L+R (mono mix) scaled by gain.
 */
public class Adc extends ChuckUGen {
    private volatile float inputL = 0f;
    private volatile float inputR = 0f;

    public Adc() {}

    /** Called by ChuckAudio each sample before vm.advanceTime(1). */
    public void setInputSample(int channel, float value) {
        if (channel == 0) inputL = value;
        else              inputR = value;
    }

    public float getInputLeft()  { return inputL; }
    public float getInputRight() { return inputR; }

    @Override
    protected float compute(float ignored) {
        // Mono mix of stereo input, scaled by gain
        return ((inputL + inputR) * 0.5f) * gain;
    }
}
