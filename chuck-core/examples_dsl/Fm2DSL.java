import org.chuck.audio.osc.SinOsc;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * Simple FM Synthesis (sync=2 mode) using the Java Fluent DSL.
 * A modulator SinOsc drives a carrier SinOsc in FM mode.
 *
 * Original ChucK (fm2.ck):
 *   SinOsc m => SinOsc c => dac;
 *   440 => c.freq;  110 => m.freq;  300 => m.gain;  2 => c.sync;
 *   while(true) 1::second => now;
 */
public class Fm2DSL implements Shred {

    @Override
    public void shred() {
        SinOsc m = new SinOsc(sampleRate());
        SinOsc c = new SinOsc(sampleRate());

        m.chuck(c).chuck(dac());

        c.freq(440.0);
        m.freq(110.0);
        m.gain(300.0);
        c.sync(2);   // FM: carrier freq = base + modulator output

        advance(second(3.0));
    }
}
