package org.chuck.audio;

import java.util.ArrayList;
import java.util.List;

/**
 * LiSa: Live Sampling Utility.
 * Allows for live recording and multi-voice playback of an internal buffer.
 * Supports granular synthesis features like ramping, variable rates, and looping.
 */
public class LiSa extends ChuckUGen {
    private float[] buffer;
    private int recPos = 0;
    private boolean isRecording = false;
    private final float sampleRate;
    
    private final Voice[] voices;
    private int activeVoices = 1;

    public LiSa(float sampleRate) {
        this.sampleRate = sampleRate;
        this.buffer = new float[(int)sampleRate]; // 1 second default
        this.voices = new Voice[10]; // Max 10 voices
        for (int i = 0; i < voices.length; i++) {
            voices[i] = new Voice();
        }
    }

    // --- Global Parameters ---
    public void duration(long samples) {
        this.buffer = new float[(int)samples];
        this.recPos = 0;
    }

    public void record(int state) { this.isRecording = (state != 0); }

    // --- Voice Parameters (voice index 0..9) ---
    public void play(int v, int state) { if(v>=0 && v<10) voices[v].playing = (state != 0); }
    public void pos(int v, long samples) { if(v>=0 && v<10) voices[v].playPos = samples; }
    public void rate(int v, float r) { if(v>=0 && v<10) voices[v].rate = r; }
    public void loop(int v, int state) { if(v>=0 && v<10) voices[v].looping = (state != 0); }
    public void voiceGain(int v, float g) { if(v>=0 && v<10) voices[v].gain = g; }
    
    public void rampUp(int v, long samples) {
        if(v>=0 && v<10) {
            voices[v].rampSamples = samples;
            voices[v].currentRamp = 0;
            voices[v].rampStatus = 1; // UP
            voices[v].playing = true;
        }
    }

    public void rampDown(int v, long samples) {
        if(v>=0 && v<10) {
            voices[v].rampSamples = samples;
            voices[v].currentRamp = samples;
            voices[v].rampStatus = 2; // DOWN
        }
    }

    @Override
    protected float compute(float input) {
        // 1. Handle Recording
        if (isRecording && buffer.length > 0) {
            buffer[recPos] = input;
            recPos = (recPos + 1) % buffer.length;
        }

        // 2. Handle Playback (Sum all active voices)
        float output = 0.0f;
        for (int i = 0; i < voices.length; i++) {
            Voice v = voices[i];
            if (!v.playing || buffer.length == 0) continue;

            // Linear Interpolation
            int i0 = (int) v.playPos;
            int i1 = (i0 + 1) % buffer.length;
            float frac = (float) (v.playPos - i0);
            float s = buffer[i0] + (buffer[i1] - buffer[i0]) * frac;

            // Ramping
            float rampMult = 1.0f;
            if (v.rampStatus == 1) { // Up
                v.currentRamp++;
                rampMult = (float) v.currentRamp / v.rampSamples;
                if (v.currentRamp >= v.rampSamples) v.rampStatus = 0;
            } else if (v.rampStatus == 2) { // Down
                v.currentRamp--;
                rampMult = (float) v.currentRamp / v.rampSamples;
                if (v.currentRamp <= 0) {
                    v.rampStatus = 0;
                    v.playing = false;
                }
            }

            output += s * v.gain * rampMult;

            // Advance play position
            v.playPos += v.rate;
            if (v.playPos >= buffer.length) {
                if (v.looping) v.playPos %= buffer.length;
                else v.playing = false;
            } else if (v.playPos < 0) {
                if (v.looping) v.playPos = buffer.length + (v.playPos % buffer.length);
                else v.playing = false;
            }
        }

        lastOut = output;
        return output;
    }

    private static class Voice {
        double playPos = 0;
        float rate = 1.0f;
        boolean playing = false;
        boolean looping = false;
        float gain = 1.0f;
        
        int rampStatus = 0; // 0: none, 1: up, 2: down
        long rampSamples = 0;
        long currentRamp = 0;
    }
}
