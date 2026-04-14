import org.chuck.audio.stk.Wurley;
import org.chuck.audio.fx.JCRev;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * Wurley (FM Electric Piano) example using the Java Fluent DSL.
 * Plays random notes from a minor pentatonic scale with slight frequency drift.
 *
 * Original ChucK (stk/wurley.ck):
 *   Wurley voc => JCRev r => dac;
 *   while(true) { random note; random velocity => voc.noteOn; 250::ms => now; }
 */
public class WurleyDSL implements Shred {

    private static final int[] SCALE = {0, 3, 7, 8, 11};  // minor pentatonic

    private static double mtof(int midi) {
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
    }

    @Override
    public void shred() {
        Wurley voc = new Wurley(sampleRate());
        JCRev r = new JCRev(sampleRate());

        voc.chuck(r).chuck(dac());
        voc.gain(0.95);
        r.gain(0.8f);
        r.mix(0.1f);

        // play 10 notes
        for (int i = 0; i < 10; i++) {
            int degree = (int)(Math.random() * SCALE.length);
            int octave = (int)(Math.random() * 2);
            int midi = 45 + octave * 12 + SCALE[degree];

            voc.setFreq(mtof(midi));
            voc.noteOn((float)(Math.random() * 0.2 + 0.6));
            advance(ms(250));
        }

        voc.noteOff(0.5f);
        advance(ms(500));
    }
}
