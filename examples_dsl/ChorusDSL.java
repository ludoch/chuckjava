import org.chuck.audio.SinOsc;
import org.chuck.audio.Chorus;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * 4-voice Chorus effect example using the Java Fluent DSL.
 * Four SinOsc voices at chord pitches (D minor 7) run through individual
 * Chorus units, producing a shimmering, wide stereo texture.
 *
 * Original ChucK (effects/chorus.ck):
 *   SinOsc s[4]; Chorus chor[4]; [62,65,69,72] => notes;
 *   s[i] => chor[i] => dac; chor[i].modDepth(.4); chor[i].modFreq(1); chor[i].mix(.2);
 *   while(true) 1::second => now;
 */
public class ChorusDSL implements Shred {

    private static double mtof(int midi) {
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
    }

    @Override
    public void shred() {
        int[] notes = {62, 65, 69, 72};  // D4, F4, A4, C5 (Dm7)

        SinOsc[] s    = new SinOsc[4];
        Chorus[] chor = new Chorus[4];

        for (int i = 0; i < 4; i++) {
            s[i]    = new SinOsc(sampleRate());
            chor[i] = new Chorus(sampleRate());

            s[i].chuck(chor[i]).chuck(dac());
            s[i].gain(0.2);
            s[i].freq(mtof(notes[i]));

            chor[i].setModDepth(0.4f);
            chor[i].setModFreq(1.0);
            chor[i].setMix(0.2f);
        }

        // sustain for 3 seconds
        advance(second(3.0));
    }
}
