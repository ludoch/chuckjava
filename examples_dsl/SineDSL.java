import org.chuck.audio.SinOsc;
import org.chuck.core.ChuckDSL;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * Basic Sine Wave example using the Java Fluent DSL.
 */
public class SineDSL implements Shred {
    @Override
    public void shred() {
        // ChucK: SinOsc s => dac;
        SinOsc s = new SinOsc(44100);
        s.chuck(dac());
        
        // ChucK: 440 => s.freq;
        s.freq(440.0);
        
        System.out.println("Playing sine wave for 2 seconds...");
        
        // ChucK: 2::second => now;
        advance(second(2.0));
        
        System.out.println("Done.");
    }
}
