package org.chuck.audio;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;

public class Mesh2DTest {

    private static final int SAMPLE_RATE = 44100;

    private double runAndMeasure(ChuckVM vm, double seconds) {
        double maxRms = 0.0;
        long totalSamples = (long)(seconds * SAMPLE_RATE);
        for (long i = 0; i < totalSamples; i++) {
            vm.advanceTime(1);
            float sumSq = 0;
            for (int c = 0; c < vm.getNumChannels(); c++) {
                float s = vm.getChannelLastOut(c);
                sumSq += s * s;
            }
            double rms = Math.sqrt(sumSq / vm.getNumChannels());
            if (rms > maxRms) maxRms = rms;
        }
        return maxRms;
    }

    @Test
    public void testMesh2D() throws InterruptedException {
        ChuckVM vm = new ChuckVM(SAMPLE_RATE);
        String code = 
            "Mesh2D m => dac; " +
            "10 => m.nx; " +
            "10 => m.ny; " +
            "0.5 => m.x; " +
            "0.5 => m.y; " +
            "0.99 => m.decay; " +
            "m.noteOn(1.0); " +
            "1::second => now;";
        
        vm.run(code, "test");
        double maxRms = runAndMeasure(vm, 0.5);
        assertTrue(maxRms > 0.001, "Mesh2D produced no sound (maxRms=" + maxRms + ")");
    }
}
