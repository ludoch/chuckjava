package org.chuck.audio;

import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class Pan2Test {
    
    @Test
    public void testPan2NoStackOverflow() {
        // This test verifies that connecting Pan2 to dac doesn't cause stack overflow
        ChuckVM vm = new ChuckVM(44100);
        
        // Create the audio chain: Impulse -> BiQuad -> Pan2 -> dac
        Impulse impulse = new Impulse();
        BiQuad filter = new BiQuad(44100f);
        Pan2 pan = new Pan2();
        
        // Connect them
        impulse.chuckTo(filter);
        filter.chuckTo(pan);
        
        // Connect to dac channels (this should not cause stack overflow)
        pan.chuckTo(vm.getDacChannel(0));
        pan.chuckTo(vm.getDacChannel(1));
        
        // Set some parameters
        filter.setPrad(0.99f);
        filter.setEqzs(1.0f);
        filter.setGain(0.5f);
        filter.setPfreq(1000.0f);
        
        pan.setPan(0.5f);
        
        // Trigger an impulse
        impulse.setNext(1.0f);
        
        // Tick the dac channels - this should work without stack overflow
        try {
            vm.getDacChannel(0).tick(0);
            vm.getDacChannel(1).tick(0);
            
            // If we get here, no stack overflow occurred
            assertTrue(true, "No stack overflow should occur");
        } catch (StackOverflowError e) {
            fail("Stack overflow occurred when connecting Pan2 to dac: " + e.getMessage());
        }
    }
    
    @Test
    public void testPan2Panning() {
        // Test left panning
        Pan2 pan1 = new Pan2();
        pan1.setPan(-1.0f);
        pan1.setPanType(0); // Linear
        
        Impulse impulse1 = new Impulse();
        impulse1.chuckTo(pan1);
        impulse1.setNext(1.0f);
        pan1.tick(0);
        
        // Left should be full, right should be silent
        assertEquals(1.0f, pan1.getLastOutLeft(), 0.001f, "Left channel should be full volume");
        assertEquals(0.0f, pan1.getLastOutRight(), 0.001f, "Right channel should be silent");
        
        // Test right panning
        Pan2 pan2 = new Pan2();
        pan2.setPan(1.0f);
        pan2.setPanType(0); // Linear
        
        Impulse impulse2 = new Impulse();
        impulse2.chuckTo(pan2);
        impulse2.setNext(1.0f);
        pan2.tick(0);
        
        // Right should be full, left should be silent
        assertEquals(1.0f, pan2.getLastOutRight(), 0.001f, "Right channel should be full volume");
        assertEquals(0.0f, pan2.getLastOutLeft(), 0.001f, "Left channel should be silent");
        
        // Test center panning
        Pan2 pan3 = new Pan2();
        pan3.setPan(0.0f);
        pan3.setPanType(0); // Linear
        
        Impulse impulse3 = new Impulse();
        impulse3.chuckTo(pan3);
        impulse3.setNext(1.0f);
        pan3.tick(0);
        
        // Both channels should be equal
        assertEquals(pan3.getLastOutLeft(), pan3.getLastOutRight(), 0.001f, "Left and right should be equal at center");
        
        // Test constant power panning
        Pan2 pan4 = new Pan2();
        pan4.setPan(0.5f);
        pan4.setPanType(1); // Constant power
        
        Impulse impulse4 = new Impulse();
        impulse4.chuckTo(pan4);
        impulse4.setNext(1.0f);
        pan4.tick(0);
        
        // For constant power panning at 0.5, we expect specific ratios
        // cos(π/4) ≈ 0.7071, sin(π/4) ≈ 0.7071 for center, but at 0.5 pan:
        // angle = (0.5 + 1) * π/4 = 1.5 * π/4 = 3π/8 ≈ 67.5 degrees
        // cos(3π/8) ≈ 0.3827, sin(3π/8) ≈ 0.9239
        assertEquals(0.3827f, pan4.getLastOutLeft(), 0.001f, "Left channel should have correct constant power gain");
        assertEquals(0.9239f, pan4.getLastOutRight(), 0.001f, "Right channel should have correct constant power gain");
    }
}