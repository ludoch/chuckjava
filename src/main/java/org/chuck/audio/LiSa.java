package org.chuck.audio;


/**
 * LiSa: Live Sampling Utility.
 */
public class LiSa extends ChuckUGen {
    private float[] buffer;
    private int recPos = 0;
    private boolean isRecording = false;
    @SuppressWarnings("unused")
    private final float sampleRate;
    
    private final Voice[] voices;

    public LiSa(float sampleRate) {
        this.sampleRate = sampleRate;
        this.buffer = new float[(int)sampleRate]; // 1 second default
        this.voices = new Voice[10]; // Max 10 voices
        for (int i = 0; i < voices.length; i++) {
            voices[i] = new Voice();
        }
    }

    public void duration(long samples) {
        this.buffer = new float[(int)samples];
        this.recPos = 0;
    }

    public void record(int state) { this.isRecording = (state != 0); }
    public void play(int state) { voices[0].playing = (state != 0); }
    public void loop(int state) { 
        voices[0].looping = (state != 0); 
        if (state == 0) voices[0].dir = 1;
    }
    public void loop0(int state) { 
        voices[0].looping = (state != 0); 
        if (state == 0) voices[0].dir = 1;
    }
    public void bi(int state) { voices[0].bidirectional = (state != 0); }
    public double playPos() { return voices[0].playPos; }

    public void play(int v, int state) { if(v>=0 && v<10) voices[v].playing = (state != 0); }
    public void pos(int v, long samples) { if(v>=0 && v<10) voices[v].playPos = samples; }
    public void rate(int v, float r) { if(v>=0 && v<10) voices[v].rate = r; }
    public void loop(int v, int state) { if(v>=0 && v<10) voices[v].looping = (state != 0); }
    
    @Override
    protected float compute(float input, long systemTime) {
        if (isRecording && buffer.length > 0) {
            buffer[recPos] = input;
            recPos = (recPos + 1) % buffer.length;
        }

        float output = 0.0f;
        for (int i = 0; i < voices.length; i++) {
            Voice v = voices[i];
            if (!v.playing || buffer.length == 0) continue;

            int i0 = (int) v.playPos;
            int i1 = (i0 + 1) % buffer.length;
            float frac = (float) (v.playPos - i0);
            float s = buffer[i0] + (buffer[i1] - buffer[i0]) * frac;

            output += s * v.gain;

            // Advance play position
            v.playPos += v.rate * v.dir;
            if (v.playPos >= buffer.length) {
                if (v.bidirectional && v.looping) {
                    v.playPos = Math.max(0, buffer.length - 1);
                    v.dir = -1;
                } else if (v.looping) {
                    v.playPos %= buffer.length;
                } else {
                    v.playPos = buffer.length - 1; // stay at end
                    v.playing = false;
                }
            } else if (v.playPos < 0) {
                if (v.bidirectional && v.looping) {
                    v.playPos = 0;
                    v.dir = 1;
                } else if (v.looping) {
                    v.playPos = buffer.length + (v.playPos % buffer.length);
                } else {
                    v.playPos = 0; // stay at start
                    v.playing = false;
                }
            }
        }
        return output;
    }

    private static class Voice {
        double playPos = 0;
        float rate = 1.0f;
        int dir = 1;
        boolean playing = false;
        boolean looping = false;
        boolean bidirectional = false;
        float gain = 1.0f;
    }
}
