import org.chuck.audio.TriOsc;
import org.chuck.audio.Adsr;
import org.chuck.core.ChuckDSL;
import org.chuck.core.Shred;
import org.chuck.core.ChuckVM;
import static org.chuck.core.ChuckDSL.*;

/**
 * Polyphony and Concurrency example using the Java Fluent DSL.
 * Demonstrates spawning multiple Java-based shreds.
 */
public class PolyphonyDSL implements Shred {
    @Override
    public void shred() {
        // Access current VM to spork more shreds
        ChuckVM vm = org.chuck.core.ChuckVM.CURRENT_VM.get();
        
        // Spawn 5 overlapping notes
        for (int i = 0; i < 5; i++) {
            final double freq = 220.0 * (i + 1);
            
            vm.spork(() -> {
                TriOsc tri = new TriOsc(44100);
                Adsr env = new Adsr(44100);
                
                tri.chuck(env).chuck(dac());
                tri.freq(freq);
                env.set(0.5f, 0.2f, 0.3f, 1.0f);
                
                env.keyOn();
                advance(second(1.0));
                
                env.keyOff();
                advance(second(1.0));
            });
            
            // Wait 200ms before spawning next note
            advance(ms(200));
        }
        
        advance(second(3.0));
    }
}
