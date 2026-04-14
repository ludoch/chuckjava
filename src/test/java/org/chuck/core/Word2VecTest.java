package org.chuck.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Paths;
import org.chuck.core.ai.Word2Vec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for Word2Vec embedding lookup and similarity search. */
public class Word2VecTest {

  private static final String MODEL_PATH =
      Paths.get("src/test/resources/word2vec_test.txt").toAbsolutePath().toString();

  private Word2Vec w2v;

  @BeforeEach
  void setUp() {
    w2v = new Word2Vec();
    long ok = w2v.load(MODEL_PATH);
    assertEquals(1L, ok, "should load successfully");
  }

  @Test
  void testLoadAndSize() {
    assertEquals(4L, w2v.size());
    assertEquals(3L, w2v.dim());
  }

  @Test
  void testGetVector() {
    ChuckArray result = new ChuckArray(ChuckType.ARRAY, 3);
    long found = w2v.getVector("cat", result);
    assertEquals(1L, found, "cat should be in vocabulary");
    assertEquals(0.1f, (float) result.getFloat(0), 1e-5f);
    assertEquals(0.2f, (float) result.getFloat(1), 1e-5f);
    assertEquals(0.3f, (float) result.getFloat(2), 1e-5f);
  }

  @Test
  void testGetVectorOov() {
    ChuckArray result = new ChuckArray(ChuckType.ARRAY, 3);
    long found = w2v.getVector("elephant", result);
    assertEquals(0L, found, "out-of-vocabulary word should return 0");
  }

  @Test
  void testGetSimilarByWord() {
    ChuckArray results = new ChuckArray(ChuckType.ARRAY, 2);
    long n = w2v.getSimilar("cat", 2, results);
    assertEquals(2L, n);
    // Results must be non-null ChuckString objects
    assertTrue(results.getObject(0) instanceof ChuckString);
    assertTrue(results.getObject(1) instanceof ChuckString);
    // "cat" itself should not be in the results
    String first = results.getObject(0).toString();
    String second = results.getObject(1).toString();
    assertNotEquals("cat", first);
    assertNotEquals("cat", second);
  }

  @Test
  void testGetSimilarByVector() {
    // Use dog's vector: should find dog and similar words
    ChuckArray vec = new ChuckArray(ChuckType.ARRAY, 3);
    vec.setFloat(0, 0.4f);
    vec.setFloat(1, 0.5f);
    vec.setFloat(2, 0.6f);
    ChuckArray results = new ChuckArray(ChuckType.ARRAY, 1);
    long n = w2v.getSimilar(vec, 1, results);
    assertEquals(1L, n);
    assertEquals("dog", results.getObject(0).toString());
  }

  @Test
  void testMinMax() {
    ChuckArray mins = new ChuckArray(ChuckType.ARRAY, 3);
    ChuckArray maxs = new ChuckArray(ChuckType.ARRAY, 3);
    long ok = w2v.minMax(mins, maxs);
    assertEquals(1L, ok);
    // dim 0: min=-0.1, max=0.7
    assertEquals(-0.1f, (float) mins.getFloat(0), 1e-5f);
    assertEquals(0.7f, (float) maxs.getFloat(0), 1e-5f);
  }

  @Test
  void testUseKDTree() {
    assertEquals(0L, w2v.useKDTree());
  }

  @Test
  void testChuckScript() {
    // End-to-end: Word2Vec type declaration and load() call from ChucK
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    String code =
        "Word2Vec w;\n"
            + "if (!w.load(\""
            + MODEL_PATH.replace("\\", "\\\\")
            + "\")) { <<< \"load failed\" >>>; }\n"
            + "<<< \"size:\", w.size() >>>;\n"
            + "<<< \"dim:\", w.dim() >>>;\n";
    vm.run(code, "test");
    vm.advanceTime(1);
    String output = out.toString();
    assertTrue(output.contains("4"), "size should be 4, got: " + output);
    assertTrue(output.contains("3"), "dim should be 3, got: " + output);
  }

  @Test
  void testCaseInsensitiveLoad() {
    ChuckArray result = new ChuckArray(ChuckType.ARRAY, 3);
    // Model stores keys lowercase; lookup should work for uppercase input
    long found = w2v.getVector("CAT", result);
    assertEquals(1L, found, "lookup should be case-insensitive");
  }
}
