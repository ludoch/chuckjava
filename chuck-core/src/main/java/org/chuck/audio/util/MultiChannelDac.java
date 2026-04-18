package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.ChuckType;

/**
 * A proxy UGen representing the multi-channel DAC. Connections to this UGen are distributed to the
 * actual dacChannels in the VM.
 */
public class MultiChannelDac extends ChuckUGen {
  private final ChuckUGen[] dacChannels;

  public MultiChannelDac(ChuckUGen[] dacChannels) {
    super(new ChuckType("dac", ChuckType.OBJECT, 0, 0));
    this.dacChannels = dacChannels;
    this.numInputs = dacChannels.length;
  }

  @Override
  public void addSource(ChuckUGen src) {
    // If source is multi-channel, connect 1-to-1
    // If source is mono, connect to all DAC channels
    if (src.getNumOutputs() > 1) {
      for (int i = 0; i < dacChannels.length; i++) {
        src.getOutputChannel(i).chuckTo(dacChannels[i]);
      }
    } else {
      for (ChuckUGen dacChannel : dacChannels) {
        src.chuckTo(dacChannel);
      }
    }
  }

  @Override
  public void removeSource(ChuckUGen src) {
    if (src.getNumOutputs() > 1) {
      for (int i = 0; i < dacChannels.length; i++) {
        src.getOutputChannel(i).unchuck(dacChannels[i]);
      }
    } else {
      for (ChuckUGen dacChannel : dacChannels) {
        src.unchuck(dacChannel);
      }
    }
  }

  @Override
  protected float compute(float input, long systemTime) {
    return 0; // Not used as a UGen itself
  }
}
