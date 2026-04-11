package org.chuck.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;

public class ChuckTypeMethodsTest {

  private List<String> runChuck(String code) throws InterruptedException {
    ChuckVM vm = new ChuckVM(44100);
    List<String> output = java.util.Collections.synchronizedList(new ArrayList<>());
    vm.addPrintListener(
        s -> {
          String trimmed = s.trim();
          if (!trimmed.isEmpty()) {
            output.add(trimmed);
          }
        });
    int id = vm.run(code, "test");
    assertTrue(id > 0, "Failed to compile or spork shred");

    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < 500) {
      vm.advanceTime(100);
      Thread.sleep(10);
      if (vm.getNumShreds() == 0) break;
    }
    return output;
  }

  @Test
  public void testTypeofMethods() throws InterruptedException {
    String code =
        "class MyClass { "
            + "    fun void foo(int a) {} "
            + "    fun float bar() { return 0.0; } "
            + "} "
            + "MyClass mc; "
            + "typeof(mc) => Type t; "
            + "t.methods() => Function[] m; "
            + "<<< m.size() >>>; "
            + "for (0 => int i; i < m.size(); i++) { "
            + "    <<< m[i].name() >>>; "
            + "    <<< m[i].numArgs() >>>; "
            + "    <<< m[i].returnType() >>>; "
            + "}";

    List<String> out = runChuck(code);
    // Expect size 2 + outputs for foo and bar
    assertTrue(out.size() >= 7, "Expected at least 7 lines of output. Got: " + out);

    assertEquals("2", out.get(0));

    // Output order might vary, but let's check content loosely
    assertTrue(out.contains("foo"), "missing foo");
    assertTrue(out.contains("1"), "missing arg count 1 for foo");
    assertTrue(out.contains("void"), "missing return type void for foo");

    assertTrue(out.contains("bar"), "missing bar");
    assertTrue(out.contains("0"), "missing arg count 0 for bar");
    assertTrue(out.contains("float"), "missing return type float for bar");
  }

  @Test
  public void testBuiltinTypeMethods() throws InterruptedException {
    String code =
        "SinOsc osc; "
            + "typeof(osc) => Type t; "
            + "t.methods() => Function[] m; "
            + "<<< m.size() > 0 >>>;";

    List<String> out = runChuck(code);
    assertEquals("1", out.get(0), "Should find methods for SinOsc");
  }
}
