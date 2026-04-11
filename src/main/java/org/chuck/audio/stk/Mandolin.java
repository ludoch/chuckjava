package org.chuck.audio.stk;

/** A mandolin physical model. */
public class Mandolin extends Plucked {
  public Mandolin(float lowestFrequency, float sampleRate) {
    super(lowestFrequency, sampleRate);
    // Mandolins have two strings, but we'll simulate with a slightly
    // shorter/more metallic pluck and tighter loop.
    loopFilter.setB0(0.45f);
    loopFilter.setB1(0.45f);
  }

  public void pluck(float velocity) {
    noteOn(velocity);
  }
}
