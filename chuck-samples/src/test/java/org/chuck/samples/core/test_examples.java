package org.chuck.samples.core;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;

public class test_examples {

  private String run(String filename) throws Exception {
    String code = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filename)));
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    try {
      vm.run(code, filename);
      vm.advanceTime(100);
    } catch (Exception e) {
      return "ERROR: " + e.getMessage();
    }
    return out.toString().trim();
  }

  @Test
  void testClassExamples() throws Exception {
    String[] files = {
      "src/main/resources/examples/class/constructors.ck",
      "src/main/resources/examples/class/ctors-dtor.ck",
      "src/main/resources/examples/class/destructor.ck",
      "src/main/resources/examples/class/dinky.ck",
      "src/main/resources/examples/class/static-init.ck",
      "src/main/resources/examples/class/super-1-simple.ck",
    };
    for (String f : files) {
      String r = run(f);
      System.out.println("[" + f + "] " + r.replace("\n", "\n"));
      assertFalse(r.startsWith("ERROR"), "Error in " + f + ": " + r);
    }
  }
}
