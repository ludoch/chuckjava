package org.chuck.examples.host;

import org.chuck.core.ChuckVM;

/**
 * Example 2: Custom Audio Callback Hosting
 * Demonstrates how to drive the ChuckVM manually within a custom audio loop.
 * This pattern is common when embedding ChucK in game engines or DAW plugins.
 */
public class CallbackHost {
    private static final int BUFFER_SIZE = 512;
    private static final int SAMPLE_RATE = 44100;

    public static void main(String[] args) throws InterruptedException {
        // 1. Setup VM
        ChuckVM vm = new ChuckVM(SAMPLE_RATE);

        // 2. Load some sound-generating code
        String code = 
            """
            SinOsc s => dac; 
            440 => s.freq; 
            0.2 => s.gain; 
            while(true) { 
                Math.random2f(200, 1000) => s.freq; 
                100::ms => now; 
            }""";
        
        System.out.println("Host: Running audio callback loop...");
        vm.run(code, "audio-callback-example");

        // 3. Simulated Audio Callback
        // Imagine this is called by your hardware's audio driver or game engine
        for (int frame = 0; frame < 200; frame++) { // Run for ~2 seconds
            float[] leftOutput = new float[BUFFER_SIZE];
            float[] rightOutput = new float[BUFFER_SIZE];

            // PULL SAMPLES FROM CHUCK
            for (int i = 0; i < BUFFER_SIZE; i++) {
                // IMPORTANT: Advance the VM by exactly 1 sample
                vm.advanceTime(1);
                
                // Pull the resulting samples from the 'dac'
                leftOutput[i] = vm.getChannelLastOut(0);
                rightOutput[i] = vm.getChannelLastOut(1);
            }

            // At this point, you would send leftOutput/rightOutput to your hardware
            if (frame % 50 == 0) {
                System.out.println("Host: Processed buffer frame " + frame);
            }
            
            // Artificial delay to simulate real-time
            Thread.sleep((long) (BUFFER_SIZE * 1000.0 / SAMPLE_RATE));
        }

        System.out.println("Host: Done.");
    }
}
