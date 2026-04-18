package org.chuck.core;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.core.ai.HMM;
import org.junit.jupiter.api.Test;

/** Tests for the expanded discrete HMM API (ChAI-compatible). */
public class HMMTest {

  @Test
  void testDiscreteHmm_loadAndGenerate() {
    HMM hmm = new HMM();
    ChuckArray initial = floatArr(0.6, 0.2, 0.2);
    ChuckArray transition =
        makeJagged(floatArr(0.8, 0.1, 0.1), floatArr(0.2, 0.7, 0.1), floatArr(0.1, 0.3, 0.6));
    ChuckArray emission =
        makeJagged(floatArr(0.7, 0.0, 0.3), floatArr(0.1, 0.9, 0.0), floatArr(0.0, 0.2, 0.8));

    assertEquals(1L, hmm.load(initial, transition, emission));

    ChuckArray results = intArr(new int[30]);
    assertEquals(30L, hmm.generate(30, results));

    // All generated symbols should be in [0, 2]
    for (int i = 0; i < 30; i++) {
      long sym = results.getInt(i);
      assertTrue(sym >= 0 && sym <= 2, "symbol " + i + " = " + sym + " out of range [0,2]");
    }
  }

  @Test
  void testDiscreteHmm_trainAndGenerate() {
    HMM hmm = new HMM();
    ChuckArray observations = intArr(0, 1, 2, 0, 1, 2, 0, 1, 2, 0, 1, 2);
    assertEquals(1L, hmm.train(2, 3, observations));

    ChuckArray results = intArr(new int[30]);
    assertEquals(30L, hmm.generate(30, results));

    for (int i = 0; i < 30; i++) {
      long sym = results.getInt(i);
      assertTrue(sym >= 0 && sym <= 2, "symbol " + i + " = " + sym + " should be in [0,2]");
    }
  }

  @Test
  void testChuckScript_hmmLoad() {
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    String code =
        "HMM hmm;\n"
            + "float init[3]; 0.6 => init[0]; 0.2 => init[1]; 0.2 => init[2];\n"
            + "float trans[3][3];\n"
            + "0.8 => trans[0][0]; 0.1 => trans[0][1]; 0.1 => trans[0][2];\n"
            + "0.2 => trans[1][0]; 0.7 => trans[1][1]; 0.1 => trans[1][2];\n"
            + "0.1 => trans[2][0]; 0.3 => trans[2][1]; 0.6 => trans[2][2];\n"
            + "float emiss[3][3];\n"
            + "0.7 => emiss[0][0]; 0.0 => emiss[0][1]; 0.3 => emiss[0][2];\n"
            + "0.1 => emiss[1][0]; 0.9 => emiss[1][1]; 0.0 => emiss[1][2];\n"
            + "0.0 => emiss[2][0]; 0.2 => emiss[2][1]; 0.8 => emiss[2][2];\n"
            + "hmm.load(init, trans, emiss);\n"
            + "int obs[10];\n"
            + "hmm.generate(10, obs);\n"
            + "<<< \"obs0:\", obs[0] >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(5);
    String output = out.toString();
    assertTrue(output.contains("obs0:"), "hmm.generate should produce output: " + output);
  }

  @Test
  void testChuckScript_hmmTrain() {
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    String code =
        "HMM hmm;\n"
            + "int obs[12];\n"
            + "0 => obs[0]; 1 => obs[1]; 2 => obs[2]; 0 => obs[3];\n"
            + "1 => obs[4]; 2 => obs[5]; 0 => obs[6]; 1 => obs[7];\n"
            + "2 => obs[8]; 0 => obs[9]; 1 => obs[10]; 2 => obs[11];\n"
            + "hmm.train(2, 3, obs);\n"
            + "int res[10];\n"
            + "hmm.generate(10, res);\n"
            + "<<< \"res0:\", res[0] >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(5);
    String output = out.toString();
    assertTrue(output.contains("res0:"), "hmm discrete train+generate: " + output);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static ChuckArray floatArr(double... vals) {
    ChuckArray a = new ChuckArray(ChuckType.ARRAY, vals.length);
    for (int i = 0; i < vals.length; i++) a.setFloat(i, vals[i]);
    return a;
  }

  private static ChuckArray intArr(int... vals) {
    ChuckArray a = new ChuckArray(ChuckType.ARRAY, vals.length);
    for (int i = 0; i < vals.length; i++) a.setInt(i, vals[i]);
    return a;
  }

  private static ChuckArray makeJagged(ChuckArray... rows) {
    ChuckArray outer = new ChuckArray(ChuckType.ARRAY, rows.length);
    for (int i = 0; i < rows.length; i++) outer.setObject(i, rows[i]);
    return outer;
  }
}
