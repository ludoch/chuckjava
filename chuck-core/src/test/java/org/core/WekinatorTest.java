package org.chuck.core;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.core.ai.Wekinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for the Wekinator AI class (train/predict regression workflow). */
public class WekinatorTest {

  private Wekinator wek;

  @BeforeEach
  void setUp() {
    wek = new Wekinator();
    wek.clear();
  }

  @Test
  void testModelAndTaskType() {
    assertEquals(Wekinator.MODEL_MLP, wek.getModelType());
    assertEquals(Wekinator.TASK_REGRESSION, wek.getTaskType());
    assertEquals("MLP", wek.modelTypeName());
    assertEquals("Regression", wek.taskTypeName());

    wek.modelType(Wekinator.MODEL_KNN);
    assertEquals("KNN", wek.modelTypeName());

    wek.taskType(Wekinator.TASK_CLASSIFICATION);
    assertEquals("Classification", wek.taskTypeName());
  }

  @Test
  void testProperties() {
    assertEquals(1L, wek.getPropertyInt(0, "hiddenLayers"));
    assertEquals(0.01, wek.getPropertyFloat(0, "learningRate"), 1e-9);

    wek.setProperty(0, "hiddenLayers", 3.0);
    assertEquals(3L, wek.getPropertyInt(0, "hiddenLayers"));

    wek.setProperty(0, "learningRate", 0.001);
    assertEquals(0.001, wek.getPropertyFloat(0, "learningRate"), 1e-9);
  }

  @Test
  void testTrainAndPredictRegression() {
    // Two classes with normalized outputs (all in [0,1]) to avoid gradient explosion:
    // input ≈ [0.1,0.1,0.1] → output = [0.9, 0.1]
    // input ≈ [0.9,0.9,0.9] → output = [0.1, 0.9]
    ChuckArray in1 = arr(0.1, 0.1, 0.1);
    ChuckArray out1 = arr(0.9, 0.1);
    ChuckArray in2 = arr(0.9, 0.9, 0.9);
    ChuckArray out2 = arr(0.1, 0.9);

    for (int i = 0; i < 20; i++) {
      wek.input(in1);
      wek.output(out1);
      wek.add();
      wek.input(in2);
      wek.output(out2);
      wek.add();
    }
    assertEquals(40L, wek.numObs());

    wek.setProperty(0, "epochs", 500.0);
    long ok = wek.train();
    assertTrue(ok > 0, "train should succeed");

    ChuckArray result = arr(0.0, 0.0);
    long pred = wek.predict(arr(0.1, 0.1, 0.1), result);
    assertEquals(1L, pred);
    double v0 = result.getFloat(0);
    assertFalse(Double.isNaN(v0), "prediction should not be NaN");
    // output[0] should be closer to 0.9 (high) than 0.1 (low)
    assertTrue(v0 > 0.4, "output[0]=" + v0 + " should trend toward 0.9");
  }

  @Test
  void testKnnModel() {
    wek.modelType(Wekinator.MODEL_KNN);
    ChuckArray in1 = arr(0.0, 0.0);
    ChuckArray out1 = arr(1.0, 0.0);
    ChuckArray in2 = arr(1.0, 1.0);
    ChuckArray out2 = arr(0.0, 1.0);
    for (int i = 0; i < 3; i++) {
      wek.input(in1);
      wek.output(out1);
      wek.add();
      wek.input(in2);
      wek.output(out2);
      wek.add();
    }
    wek.train();
    ChuckArray result = arr(0.0, 0.0);
    wek.predict(arr(0.05, 0.05), result);
    // Near (0,0) → output should be close to [1,0]
    assertTrue(result.getFloat(0) > 0.5, "knn should predict near out1");
  }

  @Test
  void testChuckScript() {
    // Verify Wekinator type is recognized by the ChucK compiler
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    String code =
        "Wekinator wek;\n"
            + "<<< \"modelType:\", wek.modelTypeName() >>>;\n"
            + "<<< \"taskType:\", wek.taskTypeName() >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(1);
    String output = out.toString();
    assertTrue(output.contains("MLP"), "got: " + output);
    assertTrue(output.contains("Regression"), "got: " + output);
  }

  @Test
  void testAiConstants() {
    // Verify AI.MLP, AI.Regression constants compile and produce expected int values
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    String code =
        "<<< \"MLP:\", AI.MLP >>>;\n"
            + "<<< \"Regression:\", AI.Regression >>>;\n"
            + "<<< \"Classification:\", AI.Classification >>>;\n"
            + "<<< \"KNN:\", AI.KNN >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(1);
    String output = out.toString();
    assertTrue(output.contains("0"), "MLP and Regression should be 0; got: " + output);
    assertTrue(output.contains("1"), "Classification and KNN should be 1; got: " + output);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static ChuckArray arr(double... vals) {
    ChuckArray a = new ChuckArray(ChuckType.ARRAY, vals.length);
    for (int i = 0; i < vals.length; i++) a.setFloat(i, vals[i]);
    return a;
  }
}
