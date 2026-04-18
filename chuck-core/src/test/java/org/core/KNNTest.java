package org.chuck.core;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.core.ai.KNN;
import org.chuck.core.ai.KNN2;
import org.junit.jupiter.api.Test;

/** Tests for expanded KNN and KNN2 APIs (matching ChAI examples). */
public class KNNTest {

  // ── KNN ──────────────────────────────────────────────────────────────────

  @Test
  void testKnn_trainFeaturesOnly_search() {
    KNN knn = new KNN();
    // train with just features (no output labels) — pure k-NN search
    ChuckArray features = makeJagged(floatArr(0.0, 0.0), floatArr(1.0, 1.0), floatArr(2.0, 2.0));
    knn.train(features);

    // search for 2 nearest to (0.5, 0.5) — should be index 0 and 1
    ChuckArray q = floatArr(0.5, 0.5);
    ChuckArray indices = intArr(0, 0);
    long found = knn.search(q, 2, indices);
    assertEquals(2L, found, "should find 2 neighbors");
    // indices should be 0 and 1
    assertTrue(
        (indices.getInt(0) == 0 && indices.getInt(1) == 1)
            || (indices.getInt(0) == 1 && indices.getInt(1) == 0),
        "nearest to (0.5,0.5) should be indices 0 and 1, got "
            + indices.getInt(0)
            + ","
            + indices.getInt(1));
  }

  @Test
  void testKnn_weigh() {
    KNN knn = new KNN();
    ChuckArray features = makeJagged(floatArr(0.0, 0.0), floatArr(1.0, 0.0), floatArr(0.0, 1.0));
    ChuckArray outputs = makeJagged(floatArr(10.0), floatArr(20.0), floatArr(30.0)); // [10, 20, 30]
    knn.train(features, outputs);

    // weight the first dimension heavily — (1.0, 0.0) should be closest to (0.9, 0.5)
    knn.weigh(floatArr(10.0, 0.1)); // strong x-weight
    ChuckArray q = floatArr(0.9, 0.5);
    ChuckArray result = floatArr(0.0);
    knn.k(1);
    knn.predict(q, result);
    assertEquals(20.0, result.getFloat(0), 1.0, "with x-heavy weight, nearest should be (1,0)→20");
  }

  @Test
  void testKnn_trainWithOutputs_predict() {
    KNN knn = new KNN();
    ChuckArray X = makeJagged(floatArr(0.0, 0.0), floatArr(1.0, 1.0), floatArr(2.0, 2.0));
    ChuckArray Y = makeJagged(floatArr(0.0), floatArr(1.0), floatArr(2.0));
    knn.train(X, Y);
    knn.k(1);

    ChuckArray result = floatArr(0.0);
    knn.predict(floatArr(0.1, 0.1), result);
    assertEquals(0.0, result.getFloat(0), 0.01, "near (0,0) should predict 0");

    knn.predict(floatArr(1.9, 1.9), result);
    assertEquals(2.0, result.getFloat(0), 0.01, "near (2,2) should predict 2");
  }

  // ── KNN2 ─────────────────────────────────────────────────────────────────

  @Test
  void testKnn2_predictWithK() {
    KNN2 knn = new KNN2();
    ChuckArray features = makeJagged(floatArr(0.0, 0.0), floatArr(1.0, 1.0), floatArr(2.0, 2.0));
    ChuckArray labels = intArr(0, 1, 2);
    knn.train(features, labels);

    // predict(query, k, prob[]) — 3 classes
    ChuckArray prob = floatArr(0.0, 0.0, 0.0);
    knn.predict(floatArr(0.5, 0.5), 2, prob);
    // With k=2, near (0.5,0.5): nearest are (0,0) and (1,1) → classes 0 and 1
    // prob[0]=0.5, prob[1]=0.5, prob[2]=0.0
    assertEquals(0.5, prob.getFloat(0), 0.01, "prob[0] should be 0.5");
    assertEquals(0.5, prob.getFloat(1), 0.01, "prob[1] should be 0.5");
    assertEquals(0.0, prob.getFloat(2), 0.01, "prob[2] should be 0.0");
  }

  @Test
  void testKnn2_searchWithK() {
    KNN2 knn = new KNN2();
    ChuckArray features = makeJagged(floatArr(0.0, 0.0), floatArr(1.0, 1.0), floatArr(2.0, 2.0));
    ChuckArray labels = intArr(0, 1, 2);
    knn.train(features, labels);

    ChuckArray indices = intArr(0, 0);
    long found = knn.search(floatArr(0.1, 0.1), 2, indices);
    assertEquals(2L, found);
    // nearest to (0.1,0.1): index 0 and index 1
    assertEquals(0L, indices.getInt(0), "first neighbor should be index 0");
    assertEquals(1L, indices.getInt(1), "second neighbor should be index 1");
  }

  @Test
  void testKnn2_weigh() {
    KNN2 knn = new KNN2();
    ChuckArray features = makeJagged(floatArr(0.0, 0.0), floatArr(1.0, 0.0), floatArr(0.0, 1.0));
    ChuckArray labels = intArr(0, 1, 2);
    knn.train(features, labels);
    knn.k(1);

    // Query (0.9, 5.0): without weights it's near (0,1) or (1,0); with x-heavy weight,
    // (1,0) is much closer along x-axis (x-dist=0.1) vs (0,0) (x-dist=0.9) or (0,1) (x-dist=0.9)
    knn.weigh(floatArr(10.0, 0.1));
    long cls = knn.predict(floatArr(0.9, 5.0));
    assertEquals(1L, cls, "x-heavy weight should make (1,0) closest → class 1");
  }

  // ── ChucK script integration ─────────────────────────────────────────────

  @Test
  void testChuckScript_knnSearch() {
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    String code =
        "KNN knn;\n"
            + "float f[3][2];\n"
            + "0.0 => f[0][0]; 0.0 => f[0][1];\n"
            + "1.0 => f[1][0]; 1.0 => f[1][1];\n"
            + "2.0 => f[2][0]; 2.0 => f[2][1];\n"
            + "knn.train(f);\n"
            + "float q[2]; 0.5 => q[0]; 0.5 => q[1];\n"
            + "int idx[2];\n"
            + "knn.search(q, 2, idx);\n"
            + "<<< \"found:\", idx[0], idx[1] >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(5);
    String output = out.toString();
    assertTrue(output.contains("found:"), "knn.search should produce output: " + output);
  }

  @Test
  void testChuckScript_knn2Classify() {
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    String code =
        "KNN2 knn;\n"
            + "float f[3][2];\n"
            + "0.0 => f[0][0]; 0.0 => f[0][1];\n"
            + "1.0 => f[1][0]; 1.0 => f[1][1];\n"
            + "2.0 => f[2][0]; 2.0 => f[2][1];\n"
            + "int labels[3]; 0 => labels[0]; 1 => labels[1]; 2 => labels[2];\n"
            + "knn.train(f, labels);\n"
            + "float q[2]; 0.5 => q[0]; 0.5 => q[1];\n"
            + "float prob[3];\n"
            + "knn.predict(q, 2, prob);\n"
            + "<<< \"prob0:\", prob[0] >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(5);
    String output = out.toString();
    assertTrue(output.contains("prob0:"), "knn2.predict should produce output: " + output);
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
