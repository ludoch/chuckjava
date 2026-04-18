import org.chuck.audio.osc.Noise;
import org.chuck.audio.filter.BPF;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * Band-Pass Filter sweep example using the Java Fluent DSL.
 * White noise through a BPF with a sweeping center frequency.
 *
 * Original ChucK (filter/bpf.ck):
 *   Noise n => BPF f => dac;  1 => f.Q;
 *   while(true) { 100 + Std.fabs(Math.sin(now/second)) * 5000 => f.freq; 5::ms => now; }
 */
public class BpfDSL implements Shred {

    @Override
    public void shred() {
        Noise n = new Noise();
        BPF f = new BPF(sampleRate());
        n.chuck(f).chuck(dac());

        f.Q(1.0);

        // sweep center frequency for 2 seconds (400 steps × 5ms)
        for (int i = 0; i < 400; i++) {
            double t = i * 0.005;
            f.freq(100.0 + Math.abs(Math.sin(t)) * 5000.0);
            advance(ms(5));
        }
    }
}
