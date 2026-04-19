package org.chuck.audio.filter;

/** Legacy alias for LPF. */
@Deprecated
public class Lpf extends LPF {
  public Lpf() {
    super();
  }

  public Lpf(float sampleRate) {
    super(sampleRate);
  }
}
