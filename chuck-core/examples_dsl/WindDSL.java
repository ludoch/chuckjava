import org.chuck.audio.osc.Noise;
import org.chuck.audio.filter.BiQuad;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * Wind Sound example using the Java Fluent DSL.
 * White noise through a BiQuad resonance filter with a sweeping resonant frequency.
 *
 * Original ChucK (wind.ck):
 *   Noise n => BiQuad f => dac;
 *   .99 => f.prad;  .05 => f.gain;  1 => f.eqzs;
 *   while(true) {
 *       100.0 + Std.fabs(Math.sin(t)) * 15000.0 => f.pfreq;
 *       t + .01 => t; 5::ms => now;
 *   }
 */
public class WindDSL implements Shred {

    @Override
    public void shred() {
        Noise n = new Noise();
        BiQuad f = new BiQuad(sampleRate());
        n.chuck(f).chuck(dac());

        f.setPrad(0.99);
        f.gain(0.05);
        f.setEqzs(1);

        // sweep for 1.5 seconds (300 steps × 5ms)
        double t = 0.0;
        for (int i = 0; i < 300; i++) {
            f.setPfreq(100.0 + Math.abs(Math.sin(t)) * 15000.0);
            t += 0.01;
            advance(ms(5));
        }
    }
}
