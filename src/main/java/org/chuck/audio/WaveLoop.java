package org.chuck.audio;

/**
 * WaveLoop: Looping waveform input UGen.
 */
public class WaveLoop extends WvIn {
    public WaveLoop() {
        super();
        setLoop(true);
    }

    public WaveLoop(float sampleRate) {
        super(sampleRate);
        setLoop(true);
    }
}
