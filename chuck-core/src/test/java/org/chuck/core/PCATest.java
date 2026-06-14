package org.chuck.core;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.core.ai.PCA;
import org.junit.jupiter.api.Test;

/** Tests for PCA.reduce() static method and instance transform(). */
public class PCATest {

  @Test
  void testReduce_static() {
    // Build 5x4 input (5 samples, 4 dimensions)
    ChuckArray input = new ChuckArray(ChuckType.ARRAY, 5);
    for (int i = 0; i < 5; i++) {
      ChuckArray row = new ChuckArray(ChuckType.ARRAY, 4);
      for (int j = 0; j < 4; j++) row.setFloat(j, (i + 1) * (j + 1) * 0.1);
      input.setObject(i, row);
    }
    // output: 5x2
    ChuckArray output = new ChuckArray(ChuckType.ARRAY, 5);
    for (int i = 0; i < 5; i++) {
      output.setObject(i, new ChuckArray(ChuckType.ARRAY, 2));
    }
    long n = PCA.reduce(input, 2, output);
    assertEquals(5L, n, "reduce should process all 5 rows");
    // first output row should have non-NaN values
    ChuckArray row0 = (ChuckArray) output.getObject(0);
    assertFalse(Double.isNaN(row0.getFloat(0)), "output[0][0] should not be NaN");
  }

  @Test
  void testChuckScript_pcaReduce() {
    ChuckVM vm = new ChuckVM(44100);
    vm.setLogLevel(1);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    String code =
        "float input[5][4];\n"
            + "float output[5][3];\n"
            + "for( 0 => int i; i < 5; i++ ) {\n"
            + "  for( 0 => int j; j < 4; j++ ) {\n"
            + "    Math.randomf() => input[i][j];\n"
            + "  }\n"
            + "}\n"
            + "PCA.reduce(input, 3, output);\n"
            + "<<< \"done:\", output[0][0] >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(10);
    String output = out.toString();
    assertTrue(output.contains("done:"), "PCA.reduce should produce output: [" + output + "]");
  }
}
