import org.chuck.audio.osc.SinOsc;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * Harmonic Series example using the Java Fluent DSL.
 * Sweeps through the first 12 harmonics of a base frequency twice.
 *
 * Original ChucK (harmonics.ck):
 *   220 => float base; 12 => int n; 125::ms => dur d;
 *   SinOsc osc => dac; osc.gain(0.5);
 *   while(true) { for(0=>int i; i<n; i++) { base+i*base => osc.freq; d => now; } }
 */
public class HarmonicsDSL implements Shred {

    @Override
    public void shred() {
        double base = 220.0;
        int numHarmonics = 12;

        SinOsc osc = new SinOsc(sampleRate());
        osc.chuck(dac());
        osc.gain(0.5);

        // play 2 sweeps through the harmonic series
        for (int sweep = 0; sweep < 2; sweep++) {
            for (int i = 0; i < numHarmonics; i++) {
                osc.freq(base + i * base);
                advance(ms(125));
            }
        }
    }
}
