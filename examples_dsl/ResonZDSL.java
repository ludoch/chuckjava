import org.chuck.audio.Noise;
import org.chuck.audio.ResonZ;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * ResonZ (resonance filter with equal-gain zeroes) example using the Java Fluent DSL.
 * White noise through a ResonZ filter with a sweeping center frequency.
 *
 * Original ChucK (filter/resonz.ck):
 *   Noise n => ResonZ f => dac;  2 => f.Q;
 *   while(true) { 100 + Std.fabs(Math.sin(now/second)) * 5000 => f.freq; 5::ms => now; }
 */
public class ResonZDSL implements Shred {

    @Override
    public void shred() {
        Noise n = new Noise();
        ResonZ f = new ResonZ(sampleRate());
        n.chuck(f).chuck(dac());

        f.setQ(2.0f);

        // sweep center frequency for 2 seconds (400 steps × 5ms)
        for (int i = 0; i < 400; i++) {
            double t = i * 0.005;
            f.setFreq((float)(100.0 + Math.abs(Math.sin(t)) * 5000.0));
            advance(ms(5));
        }
    }
}
