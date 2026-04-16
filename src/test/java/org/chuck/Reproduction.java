package org.chuck;

import java.nio.file.Files;
import java.nio.file.Paths;
import org.chuck.core.ChuckVM;

public class Reproduction {
  public static void main(String[] args) throws Exception {
    ChuckVM vm = new ChuckVM(44100);
    vm.addPrintListener(System.out::print);
    String code = Files.readString(Paths.get("examples/sequencer_setup.ck"));
    int id = vm.run(code, "sequencer_setup.ck");
    if (id <= 0) {
      System.err.println("COMPILATION FAILED");
    } else {
      System.out.println("COMPILATION SUCCESS");
    }
  }
}
