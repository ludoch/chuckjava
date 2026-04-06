import org.chuck.audio.Noise;
import org.chuck.audio.Envelope;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * Linear Envelope example using the Java Fluent DSL.
 * Runs white noise through a linear envelope for 3 bursts.
 *
 * Original ChucK (envelope.ck):
 *   Noise n => Envelope e => dac;
 *   while(true) {
 *       Math.random2f(10,500)::ms => dur t => e.duration;
 *       e.keyOn(); 800::ms => now;
 *       e.keyOff(); 800::ms => now;
 *   }
 */
public class EnvelopeDSL implements Shred {

    @Override
    public void shred() {
        Noise n = new Noise();
        Envelope e = new Envelope(sampleRate());
        n.chuck(e).chuck(dac());

        for (int i = 0; i < 3; i++) {
            // random rise/fall time between 10 ms and 500 ms
            double riseMs = Math.random() * 490 + 10;
            e.setTime((float)(riseMs / 1000.0));
            System.out.printf("rise/fall: %.1f ms%n", riseMs);

            e.keyOn();
            advance(ms(800));

            e.keyOff();
            advance(ms(800));
        }
    }
}
