import org.chuck.audio.osc.SinOsc;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * Low-Frequency Oscillator example using the Java Fluent DSL.
 * Prints the LFO value every 50 ms for 10 steps (0.5 s total).
 *
 * Original ChucK (lfo.ck):
 *   SinOsc lfo => blackhole;
 *   1::second => lfo.period;   // 1 Hz
 *   while(true) {
 *       <<< lfo.last(), "" >>>;
 *       50::ms => now;
 *   }
 */
public class LfoDSL implements Shred {

    @Override
    public void shred() {
        SinOsc lfo = new SinOsc(sampleRate());
        lfo.chuck(blackhole());

        // period = 1 second → freq = 1 Hz
        lfo.freq(1.0);

        for (int i = 0; i < 10; i++) {
            System.out.printf("lfo: %.4f%n", lfo.last());
            advance(ms(50));
        }
    }
}
