package org.chuck.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;

import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;

public class ChuckEventTimeoutTest {

    private List<String> runChuck(String code, int timeoutMs) throws InterruptedException {
        ChuckVM vm = new ChuckVM(44100);
        List<String> output = java.util.Collections.synchronizedList(new ArrayList<>());
        vm.addPrintListener(s -> {
            if (!s.startsWith("[chuck]:")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    output.add(trimmed);
                }
            }
        });
        vm.run(code, "test");
        
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            vm.advanceTime(1000);
            Thread.sleep(10);
        }
        return output;
    }

    @Test
    public void testEventTimeoutTriggered() throws InterruptedException {
        // Wait on event with 1s timeout. Trigger event after 50ms.
        // Two separate shreds: one waits on event, one signals after 50ms.
        ChuckVM vm = new ChuckVM(44100);
        List<String> output = java.util.Collections.synchronizedList(new ArrayList<>());
        vm.addPrintListener(output::add);

        // Main shred: wait on event with 1-second timeout, then print elapsed
        vm.run(
            "global Event e; " +
            "now => time start; " +
            "e.timeout(1::second); " +
            "e => now; " +
            "now - start => dur elapsed; " +
            "<<< elapsed / 1::ms >>>;",
            "waiter");

        // Signaling shred: signal event after 50ms
        vm.run(
            "50::ms => now; " +
            "global Event e; " +
            "e.signal();",
            "signaler");

        // Advance ~100ms of simulated time (should see signal at ~50ms)
        for (int i = 0; i < 150; i++) {
            vm.advanceTime(1000);
            Thread.sleep(2);
            if (!output.isEmpty()) break;
        }
        Thread.sleep(20);

        assertTrue(output.size() >= 1, "Output empty! Out: " + output);
        String last = output.get(output.size() - 1).trim();
        double elapsed = Double.parseDouble(last);
        // Should be around 50ms
        assertTrue(elapsed >= 45 && elapsed <= 65, "Expected ~50ms, got: " + elapsed + ", Out: " + output);
    }

    @Test
    public void testEventTimeoutNotTriggered() throws InterruptedException {
        // Wait on event with 50ms timeout. Event never triggers.
        String code = 
            "global Event e; " +
            "now => time start; " +
            "e.timeout(50::ms); " +
            "e => now; " +
            "now - start => dur elapsed; " +
            "<<< elapsed / 1::ms >>>;";
        
        List<String> out = runChuck(code, 500);
        assertTrue(out.size() >= 1, "Output empty! Out: " + out);
        String last = out.get(out.size() - 1);
        double elapsed = 0;
        try {
            elapsed = Double.parseDouble(last);
        } catch (NumberFormatException e) {
            fail("Expected numeric elapsed time, got: '" + last + "'. Full out: " + out);
        }
        // Should be exactly 50ms
        assertEquals(50.0, elapsed, 0.1, "Out: " + out);
    }
}
