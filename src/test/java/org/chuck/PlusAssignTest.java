package org.chuck;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.*;
import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;

public class PlusAssignTest {
  @Test
  public void testPlusAssign() throws Exception {
    ChuckVM vm = new ChuckVM(44100);
    AtomicReference<String> output = new AtomicReference<>("");
    vm.addPrintListener(s -> output.updateAndGet(old -> old + s));
    String code = "0 => int i;\n1 +=> i;\n<<< i >>>;";
    vm.run(code, "test");
    long start = System.currentTimeMillis();
    while (vm.getActiveShredCount() > 0 && (System.currentTimeMillis() - start) < 2000) {
      vm.advanceTime(4410);
      Thread.sleep(1);
    }
    vm.shutdown();
    System.out.println("Output: " + output.get());
    assertTrue(output.get().contains("1"), "Expected 1 but got: " + output.get());
  }
}
