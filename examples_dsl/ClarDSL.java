import org.chuck.audio.Clarinet;
import org.chuck.audio.JCRev;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * Clarinet Physical Model example using the Java Fluent DSL.
 * Plays a short melodic phrase through a Clarinet STK instrument with reverb.
 *
 * Original ChucK (stk/clarinet.ck):
 *   Clarinet clair => JCRev r => dac;
 *   for each note: Std.mtof(note) => clair.freq; velocity => clair.noteOn; 300::ms => now;
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
        r.setMix(0.1f);

        int[] notes = {61, 63, 65, 66, 68, 66, 65, 63, 61};

        for (int midi : notes) {
            clair.setFreq(mtof(12 + midi));
            clair.noteOn((float)(Math.random() * 0.3 + 0.6));
            advance(ms(300));
        }

        clair.noteOff(0.5f);
        advance(ms(500));
    }
}
