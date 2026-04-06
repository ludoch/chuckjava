import org.chuck.audio.Noise;
import org.chuck.audio.Gain;
import org.chuck.audio.Delay;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * Comb Filter example using the Java Fluent DSL.
 * White noise is fed into a comb filter (feedback delay network),
 * producing a pitched, metallic resonance.
 *
 * Original ChucK (comb.ck) used a single impulse; this version uses
 * noise for continuous output that is easy to verify in tests.
 *
 * Patch:
 *   Noise noise => Gain out => dac;
 *   out => Delay delay => out;  // feedback
 *
 * The delay of L samples creates resonant peaks at multiples of sr/L Hz.
 */
public class CombDSL implements Shred {

    @Override
    public void shred() {
        final double R = 0.92;   // feedback coefficient (decay per cycle)
        final int L = 200;       // delay in samples → resonant at 44100/200 = 220.5 Hz

        Noise noise = new Noise();
        Gain out = new Gain();
        // Buffer must be larger than the delay length
        Delay delay = new Delay(L + 2, (float) sampleRate());

        // feedforward: noise -> out -> dac
        noise.chuck(out).chuck(dac());
        // feedback:    out -> delay -> out
        out.chuck(delay).chuck(out);

        delay.setDelay(L);
        // Scale noise and feedback so output stays in range
        noise.gain(0.05);
        out.gain(1.0);
        delay.gain(R);

        System.out.printf("CombDSL: L=%d samples, R=%.2f, resonance ≈ %.1f Hz%n",
                L, R, sampleRate() / (double) L);

        // run for 1.5 seconds
        advance(second(1.5));
    }
}
