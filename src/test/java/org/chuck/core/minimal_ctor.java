package org.chuck.core;

import org.junit.jupiter.api.Test;

public class minimal_ctor {
  private String run(String code) {
    var vm = new ChuckVM(44100);
    var out = new StringBuilder();
    vm.addPrintListener(
        s -> {
          out.append(s);
          System.out.print("[P]" + s);
        });
    try {
      vm.run(code, "test");
      vm.advanceTime(100);
    } catch (Exception e) {
      System.out.println("[E] " + e.getMessage());
    }
    return out.toString().trim();
  }

  @Test
  void test1() {
    System.out.println("=== test1: simple zero-arg ctor ===");
    run("class Foo { 1 => int n; fun @construct() { 13 => n; <<< n >>>; } } Foo f; <<< f.n >>>;");
  }

  @Test
  void test2() {
    System.out.println("=== test2: multi-overload ===");
    run(
        "class Foo { 1 => int n; fun @construct() { 13 => n; } fun @construct(int x) { x => n; } } Foo f0; Foo f1(7); <<< f0.n, f1.n >>>;");
  }
}
