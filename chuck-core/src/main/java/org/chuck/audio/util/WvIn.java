package org.chuck.audio.util;

/** WvIn: Waveform input UGen. Loads and plays audio files. */
public class WvIn extends SndBuf {
  public WvIn() {
    super();
  }

  public WvIn(float sampleRate) {
    super(sampleRate);
  }

  public String path(String p) {
    setRead(p);
    return p;
  }
}
