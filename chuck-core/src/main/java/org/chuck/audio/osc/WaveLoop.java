package org.chuck.audio.osc;

import org.chuck.audio.util.WvIn;

/** WaveLoop: Looping waveform input UGen. */
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
