package org.chuck.sequencer;

import java.io.File;
import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckVM;

public class SequencerEngineTest {
  public static void main(String[] args) throws Exception {
    System.out.println("--- Starting Sequencer Engine Test ---");

    File f = new File("chuck-samples/src/main/resources/examples/sequencer_setup.ck");
    if (!f.exists()) {
      System.err.println("Engine script not found at " + f.getAbsolutePath());
      System.exit(1);
    }

    ChuckVM vm = new ChuckVM(44100, 2);
    vm.addPrintListener(System.out::print);

    int rows = 8;
    int cols = 16;
    ChuckArray patternArray = new ChuckArray("int", rows * cols);
    ChuckArray probabilityArray = new ChuckArray("float", rows);

    for (int i = 0; i < rows * cols; i++) patternArray.setInt(i, 0L);
    for (int i = 0; i < rows; i++) probabilityArray.setFloat(i, 1.0);

    // Enable kick on step 0
    patternArray.setInt(0, 1L);

    // SET GLOBALS BEFORE ADDING SCRIPT
    vm.setGlobalObject("seq_pattern", patternArray);
    vm.setGlobalObject("seq_probability", probabilityArray);

    System.out.println("Loading engine from: " + f.getAbsolutePath());
    int id = vm.add(f.getAbsolutePath());
    if (id < 0) {
      System.err.println("Failed to load engine!");
      System.exit(1);
    }

    System.out.println("Engine loaded. Simulating 1 second of time...");

    for (int i = 0; i < 44100; i++) {
      vm.advanceTime(1);
      if (i % 5512 == 0) {
        int step = (int) vm.getGlobalInt("seq_current_step");
        System.out.println("Time: " + i + " samples, Step: " + step);
      }
    }

    System.out.println("Final Step: " + vm.getGlobalInt("seq_current_step"));

    vm.shutdown();
    System.out.println("--- Test Finished ---");
  }
}
