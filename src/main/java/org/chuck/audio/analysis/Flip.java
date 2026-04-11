package org.chuck.audio.analysis;

import org.chuck.audio.UAna;

/**
 * Flip — copies audio buffer into UAna blob without analysis. Enables custom UAna pipelines: feed
 * raw samples into blob fvals so downstream UAnas (like FeatureCollector or user code) can process
 * them.
 *
 * <p>Usage in ChucK: adc => Flip flip => blackhole; flip.size(512); while (true) { 512::samp =>
 * now; flip.upchuck() @=> UAnaBlob blob; }
 */
public class Flip extends UAna {
  private int size;
  private float[] buffer;
  private int pos = 0;

  public Flip() {
    this(512);
  }

  public Flip(int size) {
    this.size = size;
    this.buffer = new float[size];
  }

  public void setSize(int n) {
    this.size = n;
    this.buffer = new float[n];
    this.pos = 0;
  }

  public int getSize() {
    return size;
  }

  @Override
  protected float compute(float input, long systemTime) {
    buffer[pos % size] = input;
    pos++;
    return input;
  }

  @Override
  protected void computeUAna() {
    // Copy current buffer snapshot into blob fvals
    float[] snap = buffer.clone();
    lastBlob.setFvals(snap);
  }
}
