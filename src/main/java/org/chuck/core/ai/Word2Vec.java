package org.chuck.core.ai;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckString;
import org.chuck.core.ChuckType;

/**
 * Word2Vec — loads a pre-trained GloVe / word2vec text embedding file and supports word-similarity
 * queries. Corresponds to ChucK's built-in {@code Word2Vec} class from {@code ulib_ai.cpp}.
 *
 * <p>File format (space-separated, one word per line):
 *
 * <pre>word f0 f1 f2 ... fN</pre>
 *
 * <p>Similarity search uses cosine similarity with a linear scan (O(V × d)). For a 400k × 50 GloVe
 * model this takes ~150 ms on modern hardware — acceptable for batch queries. A KD-tree path is not
 * yet implemented; {@link #useKDTree()} always returns {@code 0}.
 *
 * <p>Loaded models are cached in a static map keyed by canonical file path so that repeated calls
 * to {@code load()} on the same file reuse the already-parsed vectors.
 */
public class Word2Vec extends ChuckObject {

  // ── Static model cache ────────────────────────────────────────────────────

  private record Model(String[] words, float[][] vectors, Map<String, Integer> index) {}

  private static final ConcurrentHashMap<String, Model> CACHE = new ConcurrentHashMap<>();

  // ── Instance state ────────────────────────────────────────────────────────

  private Model model; // null until load() succeeds
  private float threshold = 0.0f; // cosine similarity threshold (not yet enforced)

  public Word2Vec() {
    super(ChuckType.OBJECT);
  }

  // ── ChucK API ─────────────────────────────────────────────────────────────

  /**
   * Load a GloVe / word2vec text file. Returns 1 on success, 0 on failure. Repeated loads on the
   * same canonical path reuse the cached model.
   */
  public long load(String filepath) {
    try {
      String canonical = new java.io.File(filepath).getCanonicalPath();
      model = CACHE.computeIfAbsent(canonical, Word2Vec::parseFile);
      return model != null ? 1L : 0L;
    } catch (IOException e) {
      return 0L;
    }
  }

  /** Number of words in the loaded vocabulary. */
  public long size() {
    return model == null ? 0L : model.words().length;
  }

  /** Embedding dimensionality. */
  public long dim() {
    return (model == null || model.vectors().length == 0) ? 0L : model.vectors()[0].length;
  }

  /**
   * Fill {@code result} with the embedding vector for {@code word}. Returns 1 if found, 0 if the
   * word is OOV.
   */
  public long getVector(String word, ChuckArray result) {
    if (model == null) return 0L;
    Integer idx = model.index().get(word.toLowerCase());
    if (idx == null) return 0L;
    float[] vec = model.vectors()[idx];
    int d = vec.length;
    for (int i = 0; i < d; i++) result.setFloat(i, vec[i]);
    return 1L;
  }

  /**
   * Fill {@code results} with the {@code k} words most similar (by cosine similarity) to {@code
   * word}. Returns the number of results actually filled.
   */
  public long getSimilar(String word, long k, ChuckArray results) {
    if (model == null) return 0L;
    Integer idx = model.index().get(word.toLowerCase());
    if (idx == null) return 0L;
    return getSimilarVec(model.vectors()[idx], k, results, idx);
  }

  /**
   * Fill {@code results} with the {@code k} words most similar to the given embedding {@code
   * vector} (as a ChuckArray of floats). Returns the number of results filled.
   */
  public long getSimilar(ChuckArray vector, long k, ChuckArray results) {
    if (model == null) return 0L;
    int d = (int) dim();
    float[] query = new float[d];
    for (int i = 0; i < Math.min(d, vector.size()); i++) query[i] = (float) vector.getFloat(i);
    return getSimilarVec(query, k, results, -1);
  }

  /**
   * Compute per-dimension min/max across all vectors, storing results in {@code mins} and {@code
   * maxs} (each pre-sized to {@code dim()}).
   */
  public long minMax(ChuckArray mins, ChuckArray maxs) {
    if (model == null) return 0L;
    int d = (int) dim();
    float[] lo = new float[d];
    float[] hi = new float[d];
    Arrays.fill(lo, Float.MAX_VALUE);
    Arrays.fill(hi, -Float.MAX_VALUE);
    for (float[] vec : model.vectors()) {
      for (int i = 0; i < d; i++) {
        if (vec[i] < lo[i]) lo[i] = vec[i];
        if (vec[i] > hi[i]) hi[i] = vec[i];
      }
    }
    for (int i = 0; i < d; i++) {
      mins.setFloat(i, lo[i]);
      maxs.setFloat(i, hi[i]);
    }
    return 1L;
  }

  /** Always returns 0 — KD-tree is not yet implemented; linear scan is used instead. */
  public long useKDTree() {
    return 0L;
  }

  /** Set cosine-similarity threshold for future queries (stored but not yet enforced). */
  public double threshold(double t) {
    threshold = (float) t;
    return threshold;
  }

  public double getThreshold() {
    return threshold;
  }

  // ── Internal helpers ──────────────────────────────────────────────────────

  private long getSimilarVec(float[] query, long k, ChuckArray results, int excludeIdx) {
    int n = model.words().length;
    int kk = (int) Math.min(k, n);
    float qNorm = norm(query);
    if (qNorm == 0f) return 0L;

    // Compute cosine similarities
    float[] sims = new float[n];
    for (int i = 0; i < n; i++) {
      if (i == excludeIdx) {
        sims[i] = -2f; // exclude the query word itself
        continue;
      }
      float[] vec = model.vectors()[i];
      float dot = 0f;
      float vn = norm(vec);
      if (vn == 0f) continue;
      for (int j = 0; j < query.length && j < vec.length; j++) dot += query[j] * vec[j];
      sims[i] = dot / (qNorm * vn);
    }

    // Partial sort: top-k by similarity (descending)
    int[] topK = partialArgMax(sims, kk);
    int filled = 0;
    for (int idx : topK) {
      results.setObject(filled, new ChuckString(model.words()[idx]));
      filled++;
    }
    return filled;
  }

  private static float norm(float[] v) {
    float s = 0f;
    for (float x : v) s += x * x;
    return (float) Math.sqrt(s);
  }

  /** Returns indices of the {@code k} largest values in {@code arr} (descending order). */
  private static int[] partialArgMax(float[] arr, int k) {
    int n = arr.length;
    int[] result = new int[k];
    boolean[] used = new boolean[n];
    for (int r = 0; r < k; r++) {
      int best = -1;
      for (int i = 0; i < n; i++) {
        if (!used[i] && (best == -1 || arr[i] > arr[best])) best = i;
      }
      if (best == -1) break;
      result[r] = best;
      used[best] = true;
    }
    return result;
  }

  // ── Model loading ─────────────────────────────────────────────────────────

  private static Model parseFile(String path) {
    List<String> wordList = new ArrayList<>();
    List<float[]> vecList = new ArrayList<>();
    Map<String, Integer> idx = new HashMap<>();
    try (BufferedReader br = new BufferedReader(new FileReader(path))) {
      String line;
      int dim = -1;
      while ((line = br.readLine()) != null) {
        if (line.isBlank()) continue;
        // Some word2vec formats have a header "vocabSize dim" — skip it
        if (wordList.isEmpty() && line.matches("\\d+ \\d+")) continue;
        int space = line.indexOf(' ');
        if (space < 0) continue;
        String word = line.substring(0, space).toLowerCase();
        String[] parts = line.substring(space + 1).split(" ");
        if (dim == -1) dim = parts.length;
        if (parts.length != dim) continue; // skip malformed lines
        float[] vec = new float[dim];
        for (int i = 0; i < dim; i++) vec[i] = Float.parseFloat(parts[i]);
        if (!idx.containsKey(word)) {
          idx.put(word, wordList.size());
          wordList.add(word);
          vecList.add(vec);
        }
      }
    } catch (IOException | NumberFormatException e) {
      return null;
    }
    String[] words = wordList.toArray(new String[0]);
    float[][] vecs = vecList.toArray(new float[0][]);
    return new Model(words, vecs, idx);
  }
}
