import org.chuck.audio.SinOsc;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * Chirp (frequency sweep) example using the Java Fluent DSL.
 * Sweeps from MIDI 127 down to 20, then 20 up to 120 with a coarser step.
 *
 * Original ChucK (chirp.ck):
 *   SinOsc s => dac;  .4 => s.gain;
 *   chirp(127, 20, 1::second);
 *   chirp(20, 120, 1.5::second, 100::ms);
 */
public class ChirpDSL implements Shred {

    private SinOsc s;

    /** MIDI note to frequency: 440 * 2^((midi-69)/12) */
    private static double mtof(double midi) {
        return 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0);
    }

    @Override
    public void shred() {
        s = new SinOsc(sampleRate());
        s.chuck(dac());
        s.gain(0.4);

        // chirp from MIDI 127 down to 20 over 1 second (default 1ms step)
        chirp(127, 20, ms(1000), ms(1));

        // chirp from MIDI 20 up to 120 over 1.5 seconds with 100ms steps
        chirp(20, 120, ms(1500), ms(100));

        System.out.println("ChirpDSL done.");
    }

    private void chirp(double src, double target, org.chuck.core.ChuckDuration duration,
                       org.chuck.core.ChuckDuration tinc) {
        double steps = duration.samples() / tinc.samples();
        double inc = (target - src) / steps;
        double freq = src;
        for (double count = 0; count < steps; count++) {
            freq += inc;
            s.freq(mtof(freq));
            advance(tinc);
        }
    }

    private void chirp(double src, double target, org.chuck.core.ChuckDuration duration) {
        chirp(src, target, duration, ms(1));
    }
}
