import org.chuck.audio.*;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * All-oscillators showcase using the Java Fluent DSL.
 * Mixes SinOsc, SawOsc, TriOsc, PulseOsc, SqrOsc, and FM (TriOsc->SinOsc)
 * and plays random pentatonic notes.
 *
 * Original ChucK (oscillatronx.ck): uses array of Osc, random note selection.
 */
public class OscillatorsDSL implements Shred {

    private static final int[] SCALE = {0, 2, 4, 7, 9};

    private static double mtof(int midi) {
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
    }

    @Override
    public void shred() {
        // All oscillators connected to DAC
        SinOsc  s   = new SinOsc(sampleRate());
        SawOsc  saw = new SawOsc(sampleRate());
        TriOsc  tri = new TriOsc(sampleRate());
        PulseOsc pul = new PulseOsc(sampleRate());
        SqrOsc  sqr = new SqrOsc(sampleRate());

        // FM pair: tri modulates sin
        TriOsc  trictrl = new TriOsc(sampleRate());
        SinOsc  sintri  = new SinOsc(sampleRate());
        trictrl.chuck(sintri);
        sintri.sync(2);       // FM mode
        trictrl.gain(100.0);

        s.chuck(dac());
        saw.chuck(dac());
        tri.chuck(dac());
        pul.chuck(dac());
        sqr.chuck(dac());
        sintri.chuck(dac());

        s.gain(0.2);
        saw.gain(0.1);
        tri.gain(0.1);
        pul.gain(0.1);
        sqr.gain(0.1);
        sintri.gain(0.1);

        // All oscillators share these for selection
        Osc[] oscillators = {s, saw, tri, pul, sqr, trictrl};

        // Play 20 random pentatonic notes
        for (int i = 0; i < 20; i++) {
            int select = (int)(Math.random() * 8);
            if (select > 5) select = 5;
            double freq = mtof(SCALE[(int)(Math.random() * SCALE.length)] + 60);
            oscillators[select].freq(freq);

            advance(ms(250));

            // vary trictrl width occasionally
            if (Math.random() > 0.5) {
                trictrl.width(Math.random() * 0.6 + 0.2);
                advance(ms(50));
            }
        }
    }
}
