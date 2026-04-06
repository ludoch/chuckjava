package org.chuck.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;

public class ChuckEventTimeoutTest {

    private List<String> runChuck(String code, int timeoutMs) throws InterruptedException {
        ChuckVM vm = new ChuckVM(44100);
        List<String> output = java.util.Collections.synchronizedList(new ArrayList<>());
        vm.addPrintListener(output::add);
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
        String code = 
            "global Event e; " +
            "Machine.eval(\"50::ms => now; global Event e; e.signal();\"); " +
            "now => time start; " +
            "e.timeout(1::second); " +
            "e => now; " +
            "now - start => dur elapsed; " +
            "<<< elapsed / 1::ms >>>;";
        
        List<String> out = runChuck(code, 500);
        assertTrue(out.size() >= 1, "Output empty! Out: " + out);
        String last = out.get(out.size() - 1).trim();
        double elapsed = Double.parseDouble(last);
        // Should be around 50ms
        assertTrue(elapsed >= 45 && elapsed <= 65, "Expected ~50ms, got: " + elapsed + ", Out: " + out);
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
        String last = out.get(out.size() - 1).trim();
        double elapsed = Double.parseDouble(last);
        // Should be exactly 50ms
        assertEquals(50.0, elapsed, 0.1, "Out: " + out);
    }
}
