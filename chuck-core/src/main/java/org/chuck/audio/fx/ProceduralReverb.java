package org.chuck.audio.fx;

import java.util.Arrays;
import org.chuck.audio.ChuckUGen;
import org.chuck.core.ChuckVM;
import org.chuck.core.doc;

@doc("ProceduralReverb: A simple comb filter reverb.")
public class ProceduralReverb extends ChuckUGen {
  private float decayFactor;
  private int delayLength;
  private float[] delayBuffer;
  private int writeIndex = 0;

  public ProceduralReverb() {
    this(0.75f, (int) (ChuckVM.CURRENT_VM.get().getSampleRate() * 0.1));
  }

  public ProceduralReverb(float decayFactor, int delayLength) {
    this.decayFactor = Math.max(0.0f, Math.min(0.9999f, decayFactor));
    this.delayLength = Math.max(1, delayLength);
    this.delayBuffer = new float[this.delayLength];
    this.writeIndex = 0;
  }

  @doc("Set decay factor (0.0 to 1.0).")
  public void decayFactor(float df) {
    this.decayFactor = Math.max(0.0f, Math.min(0.9999f, df));
    reset();
  }

  public float decayFactor() {
    return decayFactor;
  }

  @doc("Set delay length in samples.")
  public void delayLength(int dl) {
    this.delayLength = Math.max(1, dl);
    this.delayBuffer = Arrays.copyOf(delayBuffer, this.delayLength);
    reset();
  }

  public int delayLength() {
    return delayLength;
  }

  public void reset() {
    Arrays.fill(delayBuffer, 0.0f);
    writeIndex = 0;
  }

  @Override
  protected float compute(float input, long systemTime) {
    float delayedSample = delayBuffer[writeIndex];
    float feedbackSample = input + (delayedSample * decayFactor);
    delayBuffer[writeIndex] = feedbackSample;
    writeIndex = (writeIndex + 1) % delayLength;
    return feedbackSample * gain;
  }
}
