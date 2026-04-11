import org.chuck.audio.osc.Noise;
import org.chuck.audio.filter.Lpf;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * Low-Pass Filter sweep example using the Java Fluent DSL.
 * White noise through an LPF with a sweeping cutoff frequency.
 *
 * Original ChucK (filter/lpf.ck):
 *   Noise n => LPF lpf => dac;
 *   while(true) { Math.sin(now/second)*110 => Math.fabs => Std.mtof => lpf.freq; 5::ms => now; }
 */
public class LpfDSL implements Shred {

    private static double mtof(double midi) {
        return 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0);
    }

    @Override
    public void shred() {
        Noise n = new Noise();
        Lpf lpf = new Lpf(sampleRate());
        n.chuck(lpf).chuck(dac());

        // sweep cutoff for 2 seconds (400 steps × 5ms)
        for (int i = 0; i < 400; i++) {
            double t = i * 0.005;  // time in seconds
            double midi = Math.abs(Math.sin(t)) * 110.0;
            lpf.setCutoff((float) mtof(midi));
            advance(ms(5));
        }
    }
}
