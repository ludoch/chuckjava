import org.chuck.audio.util.Impulse;
import org.chuck.audio.filter.BiQuad;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * Impulse + BiQuad Resonance Filter example using the Java Fluent DSL.
 * Fires impulses through a resonant BiQuad filter while sweeping the
 * resonant frequency, producing a pitched "ping" texture.
 *
 * Inspired by ChucK examples larry.ck / moe.ck / curly.ck:
 *   Impulse i => BiQuad f => dac;
 *   .99 => f.prad;  1 => f.eqzs;  .5 => f.gain;
 *   while(true) { 1.0 => i.next; Std.fabs(Math.sin(v))*4000 => f.pfreq; v+.1=>v; 99::ms => now; }
 */
public class LarryDSL implements Shred {

    @Override
    public void shred() {
        Impulse imp = new Impulse();
        BiQuad f = new BiQuad(sampleRate());

        imp.chuck(f).chuck(dac());
        f.setPrad(0.99);
        f.setEqzs(1);
        f.gain(0.5);

        // fire 15 impulses with sweeping resonant frequency
        double v = 0.0;
        for (int i = 0; i < 15; i++) {
            imp.setNext(1.0f);
            f.setPfreq(Math.abs(Math.sin(v)) * 4000.0);
            v += 0.1;
            advance(ms(99));
        }
    }
}
