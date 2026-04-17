import static org.chuck.core.ChuckDSL.*;

import org.chuck.audio.osc.*;
import org.chuck.audio.util.*;
import org.chuck.core.Shred;

public class BarDSL implements Shred {
  @Override
  public void shred() {
    SinOsc s = new SinOsc(sampleRate());
    s.chuck(dac());
    s.gain(0.2);

    long[] hi = {0};

    while (true) {
      long note = hi[(int) (Math.random() * hi.length)];
      s.freq(mtof(45 + note));
      advance(ms(200));
    }
  }
}
