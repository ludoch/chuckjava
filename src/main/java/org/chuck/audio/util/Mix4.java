package org.chuck.audio.util;

/** Mix4: 4-channel mixer. */
public class Mix4 extends MultiChannelUGen {
  public Mix4() {
    super(4);
  }

  @Override
  protected void computeMulti(float input, long systemTime) {
    for (int i = 0; i < 4; i++) {
      lastOutChannels[i] = input;
    }
  }
}
