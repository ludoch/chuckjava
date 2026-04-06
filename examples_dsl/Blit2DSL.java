import org.chuck.audio.Blit;
import org.chuck.audio.Adsr;
import org.chuck.audio.JCRev;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * BLIT + ADSR + Reverb example using the Java Fluent DSL.
 * Plays 12 articulated pentatonic notes with attack/decay shaping and reverb.
 *
 * Original ChucK (blit2.ck):
 *   Blit s => ADSR e => JCRev r => dac;
 *   e.set(5::ms, 3::ms, .5, 5::ms);
 *   while(true) { s.freq = mtof(...); e.keyOn(); 120::ms => now; e.keyOff(); 5::ms => now; }
 */
public class Blit2DSL implements Shred {

    private static final int[] SCALE = {0, 2, 4, 7, 9, 11};

    private static double mtof(int midi) {
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
    }

    @Override
    public void shred() {
        Blit s = new Blit(sampleRate());
        Adsr e = new Adsr(sampleRate());
        JCRev r = new JCRev((float) sampleRate());

        s.chuck(e).chuck(r).chuck(dac());
        s.gain(0.5);
        r.setMix(0.05f);
        e.set(0.005f, 0.003f, 0.5f, 0.005f); // A=5ms, D=3ms, S=0.5, R=5ms

        // play 12 notes
        for (int i = 0; i < 12; i++) {
            int octave = (int)(Math.random() * 4);
            int degree = (int)(Math.random() * SCALE.length);
            int midi = 33 + octave * 12 + SCALE[degree];
            s.freq(mtof(midi));
            s.harmonics((int)(Math.random() * 5) + 1);

            e.keyOn();
            advance(ms(120));
            e.keyOff();
            advance(ms(30));
        }
    }
}
