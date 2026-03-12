package org.chuck.examples.host;

import org.chuck.core.ChuckVM;

/**
 * Example 3: Global Interoperability
 * Demonstrates how to pass state between Java and ChucK at runtime 
 * using 'global' variables.
 */
public class GlobalInterop {
    public static void main(String[] args) throws InterruptedException {
        // 1. Initialize VM
        ChuckVM vm = new ChuckVM(44100);

        // 2. ChucK code that reads a global int 'myScore'
        // In original ChucK, we would use 'global int myScore;'
        // In this Java port, the VM manages these as a shared map accessible by instructions.
        String code = 
            """
            while( true ) { 
                Machine.getGlobalInt("myScore") => int score; 
                <<< "[ChucK] Current Score is:", score >>>; 
                1::second => now; 
            }""";

        System.out.println("Host: Running interop script...");
        vm.run(code, "global-interop-example");

        // 3. Update the global value from Java
        // The script will react to these changes in its next 1-second interval.
        for (int i = 0; i <= 10; i++) {
            int newScore = i * 100;
            System.out.println("Host: Setting global score to " + newScore);
            vm.setGlobalInt("myScore", newScore);
            
            // Advance VM time by 1 second so the script wakes up
            vm.advanceTime(44100);
            
            // Real-world delay so we can see output
            Thread.sleep(500);
        }

        System.out.println("Host: Finished.");
    }
}
