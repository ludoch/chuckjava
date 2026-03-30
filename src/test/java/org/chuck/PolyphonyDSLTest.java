package org.chuck;

import org.chuck.core.ChuckVM;
import org.chuck.audio.ChuckAudio;
import org.chuck.core.ChuckDSL;
import org.junit.jupiter.api.Test;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PolyphonyDSLTest {

    @Test
    public void testPolyphonyDSLOutput() throws Exception {
        int sampleRate = 44100;
        ChuckVM vm = new ChuckVM(sampleRate);
        
        // Use a list to capture prints
        List<String> prints = Collections.synchronizedList(new ArrayList<>());
        vm.addPrintListener(prints::add);

        // Load and spork PolyphonyDSL
        Runnable polyphonyTask = ChuckDSL.load(Paths.get("examples_dsl/PolyphonyDSL.java"));
        vm.spork(polyphonyTask);

        // Process a few seconds of audio and monitor DAC output
        double maxRMS = 0;
        int secondsToTest = 2;
        int bufferSize = 512;
        
        System.out.println("Testing PolyphonyDSL for " + secondsToTest + " seconds...");
        
        for (int i = 0; i < (secondsToTest * sampleRate) / bufferSize; i++) {
            vm.advanceTime(bufferSize);
            
            // Check DAC channels directly
            float sumSq = 0;
            for (int c = 0; c < vm.getNumChannels(); c++) {
                float sample = vm.getChannelLastOut(c);
                sumSq += sample * sample;
            }
            double rms = Math.sqrt(sumSq / vm.getNumChannels());
            if (rms > maxRMS) maxRMS = rms;
            
            if (rms > 1e-6) {
                // System.out.println("  [Test] Sample " + (i * bufferSize) + " RMS: " + rms);
            }
        }

        System.out.println("Max RMS detected: " + maxRMS);
        System.out.println("Prints received: " + prints);

        assertTrue(maxRMS > 0.001, "PolyphonyDSL produced no sound (Max RMS was " + maxRMS + ")");
    }
}
