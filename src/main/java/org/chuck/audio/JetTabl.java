package org.chuck.audio;

/**
 * JetTabl — jet table lookup UGen used internally by Flute.
 * Implements a non-linear jet saturation function: output = input * (0.2 - input^2 * 0.12)
 * The input signal drives a lookup through the jet reflection curve.
 */
public class JetTabl extends ChuckUGen {

    @Override
    protected float compute(float input, long systemTime) {
        // Classic STK jet table: approximation of jet reflection coefficient
        double x = input;
        double out = x * (0.2 - x * x * 0.12);
        // Clamp to [-1, 1]
        if (out >  1.0) out =  1.0;
        if (out < -1.0) out = -1.0;
        return (float) out;
    }
}
