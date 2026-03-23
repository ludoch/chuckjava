package org.chuck.audio;

import java.util.Collections;

/**
 * SFM — Spectral Flatness Measure UAna.
 *
 * Measures tonality vs. noisiness of a spectrum.
 * 0.0 = perfectly tonal (pure tone), 1.0 = perfectly noisy (white noise).
 *
 * Formula: SFM = exp(mean(log(|X[k]|))) / mean(|X[k]|)
 *   = geometric mean of magnitude / arithmetic mean of magnitude
 *
 * Connect after FFT:
 *   adc => FFT fft =^ SFM sfm;
 *   sfm.upchuck();
 *   sfm.fval()   // Spectral Flatness value 0.0–1.0
 */
public class SFM extends UAna {

    private float result = 0.0f;

    @Override
    protected float compute(float input, long systemTime) {
        return input; // pass through audio
    }

    @Override
    protected void computeUAna() {
        float[] mags = null;

        // Get magnitude spectrum from upstream FFT/UAna
        for (ChuckUGen src : sources) {
            if (src instanceof UAna u) {
                float[] fvals = u.getLastBlob().getFvals();
                if (fvals != null && fvals.length > 0) {
                    mags = fvals;
                    break;
                }
            }
        }

        if (mags == null || mags.length == 0) {
            result = 0.0f;
            lastBlob.setCvals(Collections.singletonList(new Complex(0, 0)));
            return;
        }

        double logSum = 0.0;
        double linSum = 0.0;
        int n = mags.length;

        for (float m : mags) {
            float mag = Math.max(m, 1e-10f); // avoid log(0)
            logSum += Math.log(mag);
            linSum += mag;
        }

        double geometricMean = Math.exp(logSum / n);
        double arithmeticMean = linSum / n;

        result = (arithmeticMean > 1e-10) ? (float) (geometricMean / arithmeticMean) : 0.0f;
        result = Math.max(0.0f, Math.min(1.0f, result)); // clamp to [0,1]

        lastBlob.setCvals(Collections.singletonList(new Complex(result, 0)));
    }

    /** Compute SFM directly from a magnitude spectrum (bypasses UAna graph). */
    public void computeFromSpectrum(float[] mags) {
        if (mags == null || mags.length == 0) { result = 0.0f; return; }
        double logSum = 0.0, linSum = 0.0;
        for (float m : mags) {
            float mag = Math.max(m, 1e-10f);
            logSum += Math.log(mag);
            linSum += mag;
        }
        double geoMean = Math.exp(logSum / mags.length);
        double arithMean = linSum / mags.length;
        result = (arithMean > 1e-10) ? (float)(geoMean / arithMean) : 0.0f;
        result = Math.max(0.0f, Math.min(1.0f, result));
    }

    /** Returns the last computed SFM value (0.0 = tonal, 1.0 = noisy). */
    public float getResult() { return result; }

    @Override
    public float last() { return result; }

}
