package org.chuck.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;

public class ChuckAccessControlTest {

  private List<String> errorOutput = new ArrayList<>();

  private List<String> runChuck(String code) throws InterruptedException {
    ChuckVM vm = new ChuckVM(44100);
    List<String> output = java.util.Collections.synchronizedList(new ArrayList<>());
    errorOutput.clear();
    vm.addPrintListener(
        s -> {
          String trimmed = s.trim();
          if (trimmed.startsWith("Machine.run error:")
              || trimmed.startsWith("java.lang.RuntimeException:")) {
            errorOutput.add(trimmed);
          } else if (!trimmed.isEmpty() && !trimmed.startsWith("[chuck]:")) {
            output.add(trimmed);
          }
        });
    vm.run(code, "test");

    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < 500) {
      vm.advanceTime(1000);
      Thread.sleep(10);
      if (vm.getNumShreds() == 0) break;
    }
    return output;
  }

  @Test
  public void testPrivateMethodAccessFails() throws InterruptedException {
    String code =
        "class Foo { "
            + "    private fun void bar() { <<< \"private\" >>>; } "
            + "} "
            + "Foo f; f.bar();";

    runChuck(code);
    assertTrue(
        errorOutput.stream().anyMatch(s -> s.contains("cannot access private method 'bar:0'")),
        "Should show error for private method access. Actual: " + errorOutput);
  }

  @Test
  public void testPrivateFieldAccessFails() throws InterruptedException {
    String code = "class Foo { " + "    private int x; " + "} " + "Foo f; 10 => f.x;";

    runChuck(code);
    assertTrue(
        errorOutput.stream().anyMatch(s -> s.contains("cannot access private field 'x'")),
        "Should show error for private field access. Actual: " + errorOutput);
  }

  @Test
  public void testPrivateInternalAccessWorks() throws InterruptedException {
    String code =
        "class Foo { "
            + "    private int x; "
            + "    fun void setX(int v) { v => x; } "
            + "    fun int getX() { return x; } "
            + "} "
            + "Foo f; f.setX(42); <<< f.getX() >>>;";

    List<String> out = runChuck(code);
    assertEquals(1, out.size());
    assertEquals("42", out.get(0));
  }

  @Test
  public void testProtectedAccessWorksForSubclass() throws InterruptedException {
    String code =
        "class Foo { "
            + "    protected int x; "
            + "} "
            + "class Bar extends Foo { "
            + "    fun void setX(int v) { v => x; } "
            + "    fun int getX() { return x; } "
            + "} "
            + "Bar b; b.setX(100); <<< b.getX() >>>;";

    List<String> out = runChuck(code);
    assertEquals(1, out.size(), "Output should have 1 line. Errors: " + errorOutput);
    assertEquals("100", out.get(0));
  }

  @Test
  public void testProtectedAccessFailsForNonSubclass() throws InterruptedException {
    String code = "class Foo { " + "    protected int x; " + "} " + "Foo f; 10 => f.x;";

    runChuck(code);
    assertTrue(
        errorOutput.stream().anyMatch(s -> s.contains("cannot access protected field 'x'")),
        "Should show error for protected field access. Actual: " + errorOutput);
  }
}
