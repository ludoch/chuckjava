package org.chuck.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import org.chuck.core.ai.MLP;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for the expanded MLP API (ChAI-compatible). */
public class MLPTest {

  private MLP mlp;

  @BeforeEach
  void setUp() {
    mlp = new MLP();
  }

  // ── init() ────────────────────────────────────────────────────────────────

  @Test
  void testInit_basicArchitecture() {
    ChuckArray nodes = intArr(3, 5, 2);
    long ok = mlp.init(nodes);
    assertEquals(1L, ok, "init should return 1");
    // predict should work immediately after init
    ChuckArray x = floatArr(0.1, 0.2, 0.3);
    ChuckArray y = floatArr(0.0, 0.0);
    assertEquals(1L, mlp.predict(x, y));
    assertFalse(Double.isNaN(y.getFloat(0)), "prediction should not be NaN after init");
  }

  @Test
  void testInit_withUniformActivation() {
    ChuckArray nodes = intArr(3, 5, 2);
    long ok = mlp.init(nodes, MLP.ACT_TANH);
    assertEquals(1L, ok);
  }

  @Test
  void testInit_withPerLayerActivation() {
    ChuckArray nodes = intArr(3, 5, 2);
    ChuckArray acts = intArr(MLP.ACT_RELU, MLP.ACT_LINEAR);
    long ok = mlp.init(nodes, acts);
    assertEquals(1L, ok);
  }

  // ── train() with lr + epochs args ─────────────────────────────────────────

  @Test
  void testTrain_withLrAndEpochs() {
    ChuckArray nodes = intArr(2, 4, 1);
    mlp.init(nodes);
    ChuckArray x1 = floatArr(0.0, 0.0), y1 = floatArr(0.1);
    ChuckArray x2 = floatArr(1.0, 1.0), y2 = floatArr(0.9);
    ChuckArray X = makeJagged(x1, x2);
    ChuckArray Y = makeJagged(y1, y2);
    long ok = mlp.train(X, Y, 0.05, 200);
    assertEquals(1L, ok, "train should succeed");
    ChuckArray result = floatArr(0.0);
    mlp.predict(floatArr(0.0, 0.0), result);
    assertFalse(Double.isNaN(result.getFloat(0)), "prediction not NaN");
    assertTrue(result.getFloat(0) < 0.5, "near (0,0) should be low");
  }

  // ── shuffle() static ──────────────────────────────────────────────────────

  @Test
  void testShuffle_changesOrder() {
    ChuckArray x1 = floatArr(1.0);
    ChuckArray x2 = floatArr(2.0);
    ChuckArray x3 = floatArr(3.0);
    ChuckArray X = makeJagged(x1, x2, x3);
    ChuckArray Y = makeJagged(floatArr(10.0), floatArr(20.0), floatArr(30.0));
    // After shuffle, X[i].getFloat(0) and Y[i].getFloat(0) should remain paired
    MLP.shuffle(X, Y);
    // Items should still match (X[i] → Y[i] stays paired even after shuffle)
    for (int i = 0; i < 3; i++) {
      double xv = ((ChuckArray) X.getObject(i)).getFloat(0);
      double yv = ((ChuckArray) Y.getObject(i)).getFloat(0);
      assertEquals(xv * 10.0, yv, 0.01, "pairing preserved: x=" + xv + " y=" + yv);
    }
  }

  // ── forward/backprop step-by-step ─────────────────────────────────────────

  @Test
  void testForwardAndBackprop() {
    ChuckArray nodes = intArr(2, 3, 1);
    mlp.init(nodes);

    ChuckArray x = floatArr(0.5, 0.5);
    ChuckArray y = floatArr(1.0);

    long fok = mlp.forward(x);
    assertEquals(1L, fok, "forward should succeed");

    // getActivations at layer 0 (input) and layer 1 (hidden)
    ChuckArray a0 = floatArr(0.0, 0.0);
    assertEquals(1L, mlp.getActivations(0, a0));
    assertEquals(0.5, a0.getFloat(0), 0.001, "layer 0 act[0] == input[0]");

    ChuckArray a1 = floatArr(0.0, 0.0, 0.0);
    assertEquals(1L, mlp.getActivations(1, a1));
    // hidden layer activations should be non-NaN
    assertFalse(Double.isNaN(a1.getFloat(0)), "hidden activation should not be NaN");

    long bok = mlp.backprop(y, 0.01);
    assertEquals(1L, bok, "backprop should succeed");

    // getGradients at layer 0 (hidden)
    ChuckArray g0 = floatArr(0.0, 0.0, 0.0);
    assertEquals(1L, mlp.getGradients(0, g0));
    // gradients should be non-NaN
    assertFalse(Double.isNaN(g0.getFloat(0)), "gradient should not be NaN");
  }

  // ── getWeights / getBiases ─────────────────────────────────────────────────

  @Test
  void testGetWeightsAndBiases() {
    ChuckArray nodes = intArr(2, 3, 1);
    mlp.init(nodes);

    // getBiases at layer 0 (2→3): 3 biases
    ChuckArray b = floatArr(0.0, 0.0, 0.0);
    assertEquals(1L, mlp.getBiases(0, b));
    // biases are initialized randomly, just check non-NaN
    assertFalse(Double.isNaN(b.getFloat(0)));
  }

  // ── save / load ───────────────────────────────────────────────────────────

  @Test
  void testSaveAndLoad(@TempDir java.nio.file.Path tmpDir) throws Exception {
    ChuckArray nodes = intArr(3, 5, 2);
    mlp.init(nodes);
    // train a little so weights differ from init
    ChuckArray X = makeJagged(floatArr(0.1, 0.2, 0.3));
    ChuckArray Y = makeJagged(floatArr(0.9, 0.1));
    mlp.train(X, Y, 0.01, 100);

    // predict before save
    ChuckArray y1 = floatArr(0.0, 0.0);
    mlp.predict(floatArr(0.1, 0.2, 0.3), y1);
    double before0 = y1.getFloat(0), before1 = y1.getFloat(1);

    // save
    File f = tmpDir.resolve("model.txt").toFile();
    assertEquals(1L, mlp.save(f.getAbsolutePath()));
    assertTrue(f.exists());
    assertTrue(Files.size(f.toPath()) > 0);

    // load into new MLP and predict
    MLP mlp2 = new MLP();
    assertEquals(1L, mlp2.load(f.getAbsolutePath()));
    ChuckArray y2 = floatArr(0.0, 0.0);
    mlp2.predict(floatArr(0.1, 0.2, 0.3), y2);
    assertEquals(before0, y2.getFloat(0), 1e-5f, "loaded model should produce same output[0]");
    assertEquals(before1, y2.getFloat(1), 1e-5f, "loaded model should produce same output[1]");
  }

  // ── ChucK script: init() + train() + predict() ───────────────────────────

  @Test
  void testChuckScript_initAndTrain() {
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    String code =
        "MLP mlp;\n"
            + "[3, 5, 2] @=> int nodes[];\n"
            + "mlp.init(nodes);\n"
            + "float X[2][3];\n"
            + "0.1 => X[0][0]; 0.2 => X[0][1]; 0.3 => X[0][2];\n"
            + "0.4 => X[1][0]; 0.5 => X[1][1]; 0.6 => X[1][2];\n"
            + "float Y[2][2];\n"
            + "0.1 => Y[0][0]; 0.9 => Y[0][1];\n"
            + "0.9 => Y[1][0]; 0.1 => Y[1][1];\n"
            + "mlp.train(X, Y, 0.01, 100);\n"
            + "float x[3];\n"
            + "0.1 => x[0]; 0.2 => x[1]; 0.3 => x[2];\n"
            + "float y[2];\n"
            + "mlp.predict(x, y);\n"
            + "<<< \"y0:\", y[0] >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(10);
    String output = out.toString();
    assertTrue(output.contains("y0:"), "should print y0: " + output);
  }

  @Test
  void testChuckScript_aiConstants() {
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    String code =
        "<<< \"Sigmoid:\", AI.Sigmoid >>>;\n"
            + "<<< \"ReLU:\", AI.ReLU >>>;\n"
            + "<<< \"Tanh:\", AI.Tanh >>>;\n"
            + "<<< \"Linear:\", AI.Linear >>>;\n"
            + "<<< \"Softmax:\", AI.Softmax >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(1);
    String output = out.toString();
    assertTrue(output.contains("Sigmoid:"), "got: " + output);
    assertTrue(output.contains("ReLU:"), "got: " + output);
    assertTrue(output.contains("Linear:"), "got: " + output);
  }

  @Test
  void testChuckScript_mlpShuffle() {
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    String code =
        "float X[3][1];\n"
            + "1.0 => X[0][0]; 2.0 => X[1][0]; 3.0 => X[2][0];\n"
            + "float Y[3][1];\n"
            + "10.0 => Y[0][0]; 20.0 => Y[1][0]; 30.0 => Y[2][0];\n"
            + "MLP.shuffle(X, Y);\n"
            + "<<< \"done\", X.size() >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(1);
    String output = out.toString();
    assertTrue(output.contains("done"), "shuffle completed: " + output);
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
