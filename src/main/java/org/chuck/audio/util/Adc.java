package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/**
 * Audio Device Controller (input) — the ADC UGen.
 * Provides multi-channel access to the hardware input buffer.
 */
@doc("Audio Device Controller (hardware input).")
public class Adc extends ChuckUGen {
  private float[] currentInput;

  public Adc() {
    this(2);
  }

  public Adc(int numChannels) {
    this.numOutputs = numChannels;
    this.currentInput = new float[numChannels];
    this.outputChannels = new ChuckUGen[numChannels];
    for (int i = 0; i < numChannels; i++) {
      outputChannels[i] = new AdcChannel(this, i);
    }
  }

  /** Called by the audio engine to fill the input samples for this sample period. */
  public void setInputSample(int channel, float value) {
    if (channel < currentInput.length) {
      currentInput[channel] = value;
    }
  }

  public float getInput(int channel) {
    return channel < currentInput.length ? currentInput[channel] : 0.0f;
  }

  @Override
  protected float compute(float input, long systemTime) {
    // Mono mix of first two channels for convenience
    if (numOutputs >= 2) return (currentInput[0] + currentInput[1]) * 0.5f * gain;
    return currentInput[0] * gain;
  }

  @Override
  public float getChannelLastOut(int i) {
    return getInput(i) * gain;
  }

  /** Proxy for individual ADC channels (adc.chan(i)) */
  private static class AdcChannel extends ChuckUGen {
    private final Adc parent;
    private final int channel;

    AdcChannel(Adc parent, int channel) {
      this.parent = parent;
      this.channel = channel;
    }

    @Override
    protected float compute(float input, long systemTime) {
      return parent.getInput(channel) * parent.gain;
    }
  }
}
