package org.chuck.audio.analysis;

import java.util.Collections;
import org.chuck.audio.UAna;
import org.chuck.audio.util.Complex;

/** Root Mean Square (RMS) Unit Analyzer. Measures the power of the incoming time-domain signal. */
public class RMS extends UAna {
  private float[] ring;
  private int writePos = 0;
  private int size;

  public RMS() {
    this(1024);
  }

  public RMS(int size) {
    setSize(size);
  }

  public void setSize(int size) {
    this.size = size;
    this.ring = new float[size];
    this.writePos = 0;
  }

  @Override
  protected float compute(float input, long systemTime) {
    ring[writePos] = input;
    writePos = (writePos + 1) % size;
    return input;
  }

  @Override
  protected void computeUAna() {
    double sumSquares = 0.0;
    for (float sample : ring) {
      sumSquares += sample * sample;
    }
    float rms = (float) Math.sqrt(sumSquares / size);

    // Return RMS as single fval
    lastBlob.setCvals(Collections.singletonList(new Complex(rms, 0)));
    // setCvals will set fvals[0] = rms
  }
}
