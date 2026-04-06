import org.chuck.audio.Blit;
import org.chuck.audio.JCRev;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * Band-Limited Impulse Train (BLIT) with reverb example using the Java Fluent DSL.
 * Plays 8 random pentatonic notes through a JCRev reverb.
 *
 * Original ChucK (blit.ck):
 *   Blit s => JCRev r => dac;
 *   .5 => s.gain;  .05 => r.mix;
 *   int hi[] = { 0, 2, 4, 7, 9, 11 };
 *   while(true) {
 *       Std.mtof(33 + Math.random2(0,3)*12 + hi[Math.random2(0,5)]) => s.freq;
 *       Math.random2(1,5) => s.harmonics;
 *       120::ms => now;
 *   }
 */
public class BlitDSL implements Shred {

    private static final int[] SCALE = {0, 2, 4, 7, 9, 11};

    /** MIDI note to frequency: 440 * 2^((midi-69)/12) */
    private static double mtof(int midi) {
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
    }

    @Override
    public void shred() {
        Blit s = new Blit(sampleRate());
        JCRev r = new JCRev((float) sampleRate());

        s.chuck(r).chuck(dac());
        s.gain(0.5);
        r.setMix(0.05f);

        // play 8 random notes
        for (int i = 0; i < 8; i++) {
            int octave = (int)(Math.random() * 4);           // random2(0, 3)
            int degree = (int)(Math.random() * SCALE.length); // random2(0, 5)
            int midi = 33 + octave * 12 + SCALE[degree];
            s.freq(mtof(midi));
            s.harmonics((int)(Math.random() * 5) + 1);       // random2(1, 5)
            advance(ms(120));
        }
    }
}
