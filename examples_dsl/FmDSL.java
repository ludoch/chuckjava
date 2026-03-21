import org.chuck.audio.SinOsc;
import org.chuck.audio.Adsr;
import org.chuck.core.ChuckDSL;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * FM Synthesis example using the Java Fluent DSL.
 * Demonstrates complex UGen chaining and envelopes.
 */
public class FmDSL implements Shred {
    @Override
    public void shred() {
        // ChucK: SinOsc mod => SinOsc car => ADSR env => dac;
        SinOsc mod = new SinOsc(44100);
        SinOsc car = new SinOsc(44100);
        Adsr env = new Adsr(44100);
        
        // 1. Set parameters
        car.sync(2);   // Set FM mode
        car.freq(440);
        mod.freq(220);
        mod.gain(100); // Modulation index
        env.set(0.1f, 0.1f, 0.5f, 0.5f);

        // 2. Connect the chain
        mod.chuck(car).chuck(env).chuck(dac());
        
        System.out.println("Playing FM note...");
        env.keyOn();
        advance(second(1.0));
        
        env.keyOff();
        advance(second(0.5));
        
        System.out.println("Done.");
    }
}
