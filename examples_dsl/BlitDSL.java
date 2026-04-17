import static org.chuck.core.ChuckDSL.*;

import org.chuck.audio.fx.JCRev;
import org.chuck.audio.osc.Blit;
import org.chuck.core.Shred;

public class BlitDSL implements Shred {
  @Override
  public void shred() {
    Blit s = new Blit(sampleRate());
    JCRev r = new JCRev(sampleRate());

    s.chuck(r).chuck(dac());
    s.gain(0.5);
    r.mix(0.05f);

    long[] hi = {0, 2, 4, 7, 9, 11};

    while (true) {
      double freq = mtof(33 + ((int) (Math.random() * 4) * 12) + hi[(int) (Math.random() * hi.length)]);
      s.freq(freq);
      s.harmonics((int) (Math.random() * 5) + 1);
      advance(ms(120));
    }
  }
}
