package org.chuck.audio;

/**
 * UnFlip — inverse of Flip: reads UAna blob fvals and outputs them as audio samples.
 * Useful for resynthesis pipelines (e.g., after IDCT or IFFT modification).
 *
 * Usage in ChucK:
 *   adc => Flip flip => IDCT idct => UnFlip uf => dac;
 *   while (true) { 512::samp => now; uf.upchuck(); }
 */
public class UnFlip extends UAna {
    private float[] playback = new float[0];
    private int playPos = 0;

    @Override
    protected float compute(float input, long systemTime) {
        if (playback.length == 0) return input;
        float out = playback[playPos % playback.length];
        playPos++;
        return out;
    }

    @Override
    protected void computeUAna() {
        // Pull fvals from upstream UAna and load into playback buffer
        for (ChuckUGen src : sources) {
            if (src instanceof UAna u) {
                float[] fvals = u.lastBlob.getFvals();
                if (fvals != null && fvals.length > 0) {
                    playback = fvals.clone();
                    playPos = 0;
                }
                break;
            }
        }
    }
}
