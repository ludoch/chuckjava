import org.chuck.audio.*;
import org.chuck.audio.analysis.*;
import org.chuck.audio.filter.*;
import org.chuck.audio.fx.*;
import org.chuck.audio.osc.*;
import org.chuck.audio.stk.*;
import org.chuck.audio.util.*;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

/**
 * A Java DSL demo that can be hot-reloaded by JavaMachine.
 */
public class DslDemo implements Shred {
    @Override
    public void shred() {
        // ChucK: SinOsc s => ADSR e => dac;
        SinOsc s = new SinOsc(44100);
        Adsr e = new Adsr(44100);
        
        // Fluent chaining with implicit dac()
        s.chuck(e).chuck(dac());
        
        e.set(0.1f, 0.1f, 0.5f, 0.5f);
        
        while (true) {
            float freq = (float) (Math.random() * 500 + 200);
            s.freq(freq);
            
            e.keyOn();
            advance(ms(200));
            
            e.keyOff();
       //     advance(ms(300));
        }
    }
}
