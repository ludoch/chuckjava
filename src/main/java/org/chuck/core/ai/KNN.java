package org.chuck.core.ai;

import java.util.ArrayList;
import java.util.List;
import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

/**
 * K-Nearest Neighbor — regression variant. Stores (feature_vector, output_vector) pairs and
 * predicts via weighted average of k nearest.
 */
public class KNN extends ChuckObject {

  public KNN() {
    super(ChuckType.OBJECT);
  }

  private int k = 1;
  private final List<double[]> trainX = new ArrayList<>();
  private final List<double[]> trainY = new ArrayList<>();

  public long k(long val) {
    k = (int) Math.max(1, val);
    return k;
  }

  public long getK() {
    return k;
  }

  /** train(input[][], output[][]) — ChucK passes these as ChuckArray of ChuckArrays */
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

  /** predict(input[], output[]) — fills output[] with the predicted values */
  public long predict(ChuckArray xArr, ChuckArray outArr) {
    if (trainX.isEmpty()) return 0L;
    double[] query = toDoubleArray(xArr);
    int[] indices = kNearest(query);
    int outLen = trainY.get(0).length;
    for (int j = 0; j < outLen; j++) {
      double sum = 0;
      for (int idx : indices) sum += trainY.get(idx)[j];
      outArr.setFloat(j, sum / indices.length);
    }
    return 1L;
  }

  // ------- helpers -------

  private int[] kNearest(double[] query) {
    int n = trainX.size();
    double[] dists = new double[n];
    for (int i = 0; i < n; i++) dists[i] = euclidean(query, trainX.get(i));
    // partial-sort: find k smallest
    int kk = Math.min(k, n);
    int[] idxs = new int[kk];
    boolean[] used = new boolean[n];
    for (int r = 0; r < kk; r++) {
      int best = -1;
      for (int i = 0; i < n; i++) {
        if (!used[i] && (best == -1 || dists[i] < dists[best])) best = i;
      }
      idxs[r] = best;
      used[best] = true;
    }
    return idxs;
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
