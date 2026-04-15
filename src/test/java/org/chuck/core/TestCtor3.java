package org.chuck.core;

import org.junit.jupiter.api.Test;

public class TestCtor3 {
  @Test
  void run() throws Exception {
    String code =
        new String(
            java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("examples/class/constructors.ck")));
    var vm = new ChuckVM(44100);
    var out = new StringBuilder();
    vm.addPrintListener(
        s -> {
          out.append(s);
          System.out.print("[PRINT]" + s);
        });
    try {
      vm.run(code, "test");
      vm.advanceTime(100);
    } catch (Exception e) {
      System.out.println("[ERROR] " + e.getMessage());
      e.printStackTrace();
    }
    System.out.println("\n[DONE] " + out.toString().trim());
  }
}
