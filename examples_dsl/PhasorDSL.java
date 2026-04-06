import org.chuck.audio.Phasor;
import org.chuck.audio.PulseOsc;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * Phasor-modulated PulseOsc example using the Java Fluent DSL.
 * The phasor ramps 0→1 at 1 Hz and continuously modulates the
 * pulse width of a 440 Hz PulseOsc for 2 seconds.
 *
 * Original ChucK (phasor.ck):
 *   Phasor phasor => blackhole;
 *   PulseOsc pulseOsc => dac;
 *   1.0 => phasor.freq;
 *   440.0 => pulseOsc.freq;
 *   while(true) {
 *       phasor.last() => pulseOsc.width;
 *       1::samp => now;
 *   }
 */
public class PhasorDSL implements Shred {

    @Override
    public void shred() {
        Phasor phasor = new Phasor(sampleRate());
        PulseOsc pulseOsc = new PulseOsc(sampleRate());

        phasor.chuck(blackhole());
        pulseOsc.chuck(dac());

        phasor.freq(1.0);
        pulseOsc.freq(440.0);

        // modulate width for 2 seconds, updating every 10 ms
        int steps = 200;
        for (int i = 0; i < steps; i++) {
            pulseOsc.width(phasor.last());
            advance(ms(10));
        }
    }
}
