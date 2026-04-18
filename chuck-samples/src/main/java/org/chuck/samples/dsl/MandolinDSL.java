import static org.chuck.core.ChuckDSL.*;

import org.chuck.audio.fx.JCRev;
import org.chuck.audio.stk.Mandolin;
import org.chuck.core.Shred;

public class MandolinDSL implements Shred {
  @Override
  public void shred() {
    // Mandolin needs lowestFrequency and sampleRate
    Mandolin m = new Mandolin(100.0f, sampleRate());
    JCRev r = new JCRev(sampleRate());

    m.chuck(r).chuck(dac());
    r.gain(0.75f);
    r.mix(0.025f);

    long[] notes = {61, 63, 65, 66, 68, 66, 65, 63};

    while (true) {
      // Use methods from Mandolin.java
      m.detune(0.99f + Math.random() * 0.01);
      m.pluckPos(Math.random());

      float factor = (float) (Math.random() * 3 + 1);

      for (long note : notes) {
        m.freq(mtof(((int) (Math.random() * 3) * 12) + note));
        m.pluck((float) (Math.random() * 0.3 + 0.6));
        advance(ms(100 * factor));
      }
    }
  }
}
