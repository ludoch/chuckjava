package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;

/** Identity2: 2-channel pass-through UGen. */
public class Identity2 extends StereoUGen {
  @Override
  protected void computeStereo(float input, long systemTime) {
    // StereoUGen.tick sums all sources into 'input'.
    // To be a true Identity2, we should pull from sources directly.
    float inL = 0, inR = 0;
    for (ChuckUGen src : getSources()) {
      inL += src.getChannelLastOut(0);
      inR += src.getChannelLastOut(1);
    }
    lastOutChannels[0] = inL;
    lastOutChannels[1] = inR;
  }
}
