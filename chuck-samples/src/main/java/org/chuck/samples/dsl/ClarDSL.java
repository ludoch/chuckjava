package org.chuck.samples.dsl;

import static org.chuck.core.ChuckDSL.*;

import org.chuck.audio.fx.JCRev;
import org.chuck.audio.stk.Clarinet;
import org.chuck.core.Shred;

/**
 * Clarinet Physical Model example using the Java Fluent DSL. Plays a short melodic phrase through a
 * Clarinet STK instrument with reverb.
 *
 * <p>Original ChucK (stk/clarinet.ck): Clarinet clair => JCRev r => dac; for each note:
 * Std.mtof(note) => clair.freq; velocity => clair.noteOn; 300::ms => now;
 */
public class ClarDSL implements Shred {

  private static double mtof(int midi) {
    return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
  }

  @Override
  public void shred() {
    Clarinet clair = new Clarinet(64.0f, sampleRate());
    JCRev r = new JCRev(sampleRate());

    clair.chuck(r).chuck(dac());
    r.gain(0.75f);
    r.mix(0.1f);

    int[] notes = {61, 63, 65, 66, 68, 66, 65, 63, 61};

    for (int midi : notes) {
      clair.setFreq(mtof(12 + midi));
      clair.noteOn((float) (Math.random() * 0.3 + 0.6));
      advance(ms(300));
    }

    clair.noteOff(0.5f);
    advance(ms(500));
  }
}
