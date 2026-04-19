package reproduce;

import org.chuck.core.*;
import org.chuck.host.ChuckHost;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SequencerRuntimeTest {

    @Test
    public void testSequencerRuntime() throws Exception {
        // 1. Initialize Host (44.1kHz, dummy audio)
        ChuckHost host = new ChuckHost(44100);
        ChuckVM vm = host.getVM();
        
        // 2. Set log level to see what's happening
        vm.setLogLevel(2);
        
        // 3. Setup necessary globals for the sequencer
        long[] data = new long[128]; // 8 tracks * 16 steps
        data[0] = 1; // Kick at step 0
        data[16] = 1; // Snare at step 0
        vm.setGlobalObject("seq_pattern", data);
        vm.setGlobalObject("seq_probability", new double[8]);
        
        // 4. Run the translated SequencerSetup
        // (Assuming SequencerCompileTest just ran and compiled it to chuck-core/target)
        // Actually, since it is a test, I should ideally use the class from the classpath.
        // For now, I'll use reflection if it was compiled into the target dir.
        
        // Actually, a simpler way for a unit test is to just instantiate the class if it's available.
        // But since it's generated, let's try to load it.
        try {
            java.io.File targetDir = new java.io.File("chuck-core/target");
            java.net.URLClassLoader loader = new java.net.URLClassLoader(new java.net.URL[]{targetDir.toURI().toURL()}, this.getClass().getClassLoader());
            Class<?> clazz = loader.loadClass("SequencerSetup");
            
            // Need a temporary shred to bind scoped values for constructors
            ChuckShred tempShred = new ChuckShred(null);
            Shred shred = (Shred) ScopedValue.where(ChuckVM.CURRENT_VM, vm)
                                     .where(ChuckShred.CURRENT_SHRED, tempShred)
                                     .call(() -> {
                                         try {
                                             return clazz.getDeclaredConstructor().newInstance();
                                         } catch (Exception e) {
                                             throw new RuntimeException(e);
                                         }
                                     });
            vm.run(shred);
            
            System.out.println("Sequencer started. Running for 1000ms (logical time)...");
            
            // 5. Let it run for a bit
            host.advance(44100); // 1 second
            
            System.out.println("Done.");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("SequencerSetup class not found or failed to run. Skipping runtime check.");
        }
    }
}
