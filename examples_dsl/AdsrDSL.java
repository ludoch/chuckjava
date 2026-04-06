import org.chuck.audio.SinOsc;
import org.chuck.audio.Adsr;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * ADSR Envelope example using the Java Fluent DSL.
 * Plays 4 notes shaped by an ADSR envelope.
 *
 * Original ChucK (adsr.ck):
 *   SinOsc s => ADSR e => dac;
 *   e.set(10::ms, 8::ms, .5, 500::ms);
 *   .5 => s.gain;
 *   while(true) {
 *       Math.random2(32, 96) => Std.mtof => s.freq;
 *       e.keyOn(); 500::ms => now;
 *       e.keyOff(); e.releaseTime() => now;
 *       300::ms => now;
 *   }
 */
public class AdsrDSL implements Shred {

    /** MIDI note to frequency: 440 * 2^((midi-69)/12) */
    private static double mtof(int midi) {
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
    }

    @Override
    public void shred() {
        SinOsc s = new SinOsc(sampleRate());
        Adsr e = new Adsr(sampleRate());
        s.chuck(e).chuck(dac());

        // set A=10ms, D=8ms, S=0.5, R=500ms
        e.set(0.01f, 0.008f, 0.5f, 0.5f);
        s.gain(0.5);

        // play 4 random notes
        for (int i = 0; i < 4; i++) {
            int midi = (int)(Math.random() * 65) + 32;  // random2(32, 96)
            s.freq(mtof(midi));

            e.keyOn();
            advance(ms(500));

            e.keyOff();
            // wait for release to ramp down
            long releaseSamples = Math.round(e.releaseTime());
            advance(ChuckDurationOf(releaseSamples));

            advance(ms(300));
        }
    }

    /** Build a duration from a raw sample count. */
    private static org.chuck.core.ChuckDuration ChuckDurationOf(long samples) {
        return org.chuck.core.ChuckDuration.of(samples);
    }
}
