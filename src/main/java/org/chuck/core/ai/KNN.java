package org.chuck.core.ai;

import java.util.ArrayList;
import java.util.List;
import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

/**
 * K-Nearest Neighbor — regression / nearest-neighbor search variant.
 *
 * <p>API matches ChucK ChAI: {@code train(features[][])}, {@code train(features[][], outputs[][])},
 * {@code weigh(weights[])}, {@code predict(q[], out[])}, {@code search(q[], k, indices[])}.
 */
public class KNN extends ChuckObject {

  public KNN() {
    super(ChuckType.OBJECT);
  }

  private int k = 1;
  private final List<double[]> trainX = new ArrayList<>();
  private final List<double[]> trainY = new ArrayList<>();
  private double[] weights = null; // feature weights (null = uniform)

  public long k(long val) {
    k = (int) Math.max(1, val);
    return k;
  }

  public long getK() {
    return k;
  }

  /** {@code knn.weigh(weights[])} — set per-dimension feature weights. */
  public long weigh(ChuckArray weightsArr) {
    if (weightsArr == null || weightsArr.size() == 0) {
      weights = null;
    } else {
      weights = toDoubleArray(weightsArr);
    }
    return 0L;
  }

  /** {@code knn.train(features[][])} — train for nearest-neighbor search (no output labels). */
  public long train(ChuckArray xArr) {
    trainX.clear();
    trainY.clear();
    int n = xArr.size();
    for (int i = 0; i < n; i++) {
      double[] x = toDoubleArray((ChuckArray) xArr.getObject(i));
      trainX.add(x);
      trainY.add(new double[0]); // no labels
    }
    return n;
  }

  /** {@code knn.train(features[][], outputs[][])} — train with output labels (regression). */
  public long train(ChuckArray xArr, ChuckArray yArr) {
    trainX.clear();
    trainY.clear();
    int n = xArr.size();
    for (int i = 0; i < n; i++) {
      trainX.add(toDoubleArray((ChuckArray) xArr.getObject(i)));
      trainY.add(toDoubleArray((ChuckArray) yArr.getObject(i)));
    }
    return n;
  }

  /** {@code knn.predict(input[], output[])} — weighted average of k nearest outputs. */
  public long predict(ChuckArray xArr, ChuckArray outArr) {
    if (trainX.isEmpty()) return 0L;
    double[] query = toDoubleArray(xArr);
    int[] indices = kNearest(query, k);
    if (trainY.get(0).length == 0) return 0L; // no output labels
    int outLen = trainY.get(0).length;
    for (int j = 0; j < outLen; j++) {
      double sum = 0;
      for (int idx : indices) sum += trainY.get(idx)[j];
      outArr.setFloat(j, sum / indices.length);
    }
    return 1L;
  }

  /**
   * {@code knn.search(query[], k, indices[])} — fills indices[] with the positions of k nearest
   * neighbors in the training set.
   */
  public long search(ChuckArray xArr, long kk, ChuckArray indicesArr) {
    if (trainX.isEmpty()) return 0L;
    double[] query = toDoubleArray(xArr);
    int[] indices = kNearest(query, (int) kk);
    for (int i = 0; i < indices.length && i < indicesArr.size(); i++)
      indicesArr.setInt(i, indices[i]);
    return indices.length;
  }

  // ------- helpers -------

  int[] kNearest(double[] query, int kk) {
    int n = trainX.size();
    double[] dists = new double[n];
    for (int i = 0; i < n; i++) dists[i] = weightedEuclidean(query, trainX.get(i));
    int count = Math.min(kk, n);
    int[] idxs = new int[count];
    boolean[] used = new boolean[n];
    for (int r = 0; r < count; r++) {
      int best = -1;
      for (int i = 0; i < n; i++) {
        if (!used[i] && (best == -1 || dists[i] < dists[best])) best = i;
      }
      idxs[r] = best;
      used[best] = true;
    }
    return idxs;
  }

  private double weightedEuclidean(double[] a, double[] b) {
    double s = 0;
    int len = Math.min(a.length, b.length);
    for (int i = 0; i < len; i++) {
      double d = a[i] - b[i];
      double w = (weights != null && i < weights.length) ? weights[i] : 1.0;
      s += w * w * d * d;
    }
    return Math.sqrt(s);
  }

  static double euclidean(double[] a, double[] b) {
    double s = 0;
    int len = Math.min(a.length, b.length);
    for (int i = 0; i < len; i++) {
      double d = a[i] - b[i];
      s += d * d;
    }
    return Math.sqrt(s);
  }

  static double[] toDoubleArray(ChuckArray arr) {
    if (arr == null) return new double[0];
    int n = arr.size();
    double[] d = new double[n];
    for (int i = 0; i < n; i++) d[i] = arr.getFloat(i);
    return d;
  }

  static void fillArray(ChuckArray arr, double[] vals) {
    for (int i = 0; i < vals.length; i++) arr.setFloat(i, vals[i]);
  }
}
