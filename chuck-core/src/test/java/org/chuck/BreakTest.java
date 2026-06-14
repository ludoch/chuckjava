package org.chuck;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.*;
import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;

public class BreakTest {
  @Test
  public void testNestedBreak() throws Exception {
    ChuckVM vm = new ChuckVM(44100);
    AtomicReference<String> output = new AtomicReference<>("");
    vm.addPrintListener(s -> output.updateAndGet(old -> old + s));

    String code =
"""
0 => int j;
0 => int i;
while ( j < 5 )
{
    while ( i < 10 )
    {
        1 +=> i;
        if ( i > 5 ) break;
    }
    1 +=> j;
}
if ( j == 5 ) <<< "success" >>>;
else <<< "FAIL j=", j >>>;
""";

    vm.run(code, "test");
    long start = System.currentTimeMillis();
    while (vm.getActiveShredCount() > 0 && (System.currentTimeMillis() - start) < 5000) {
      vm.advanceTime(4410);
      Thread.sleep(1);
    }
    vm.shutdown();
    System.out.println("Output: " + output.get());
    assertTrue(output.get().contains("success"), "Expected success but got: " + output.get());
  }
}
