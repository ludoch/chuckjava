package org.chuck.examples.host;

import org.chuck.core.ChuckVM;

/**
 * Example 1: Minimal Embedded Host
 * Demonstrates how to instantiate the ChuckVM and run ChucK code 
 * directly from a Java string without any audio hardware.
 */
public class MinimalHost {
    public static void main(String[] args) throws InterruptedException {
        // 1. Initialize the VM (at 44.1kHz)
        ChuckVM vm = new ChuckVM(44100);

        // 2. Define some ChucK code
        String code = 
            """
            <<< "Hello from Embedded ChucK-Java!" >>>;
            for( 0 => int i; i < 5; i++ ) {
                <<< "Iteration:", i >>>;
                100::ms => now;
            }
            """;

        System.out.println("Host: Sporking ChucK code...");
        
        // 3. Run the code string
        @SuppressWarnings("unused")
        int shredId = vm.run(code, "minimal-host-example");

        // 4. Manually drive the VM clock
        // Since there's no audio driver, we must advance time ourselves.
        // We'll advance in 10ms blocks for 1 second.
        int sampleRate = 44100;
        int stepSizeSamples = (int)(sampleRate * 0.01); // 10ms
        
        for (int i = 0; i < 100; i++) {
            vm.advanceTime(stepSizeSamples);
            Thread.sleep(10); // Sync with real-world time roughly
        }

        System.out.println("Host: Finished.");
    }
}
