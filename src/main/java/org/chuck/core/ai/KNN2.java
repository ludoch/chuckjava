package org.chuck.core.ai;

import java.util.ArrayList;
import java.util.List;
import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

/**
 * K-Nearest Neighbor — classification variant.
 *
 * <p>API matches ChucK ChAI: {@code train(features[][], labels[])}, {@code weigh(weights[])},
 * {@code predict(q[], k, prob[])}, {@code search(q[], k, indices[])}.
 */
public class KNN2 extends ChuckObject {

  public KNN2() {
    super(ChuckType.OBJECT);
  }

  private int k = 1;
  private final List<double[]> trainX = new ArrayList<>();
  private final List<Long> trainY = new ArrayList<>();
  private double[] weights = null;

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
      weights = KNN.toDoubleArray(weightsArr);
    }
    return 0L;
  }

  /** {@code knn.train(features[][], labels[])} — labels is int[]. */
  public long train(ChuckArray xArr, ChuckArray yArr) {
    trainX.clear();
    trainY.clear();
    int n = xArr.size();
    for (int i = 0; i < n; i++) {
      trainX.add(KNN.toDoubleArray((ChuckArray) xArr.getObject(i)));
      trainY.add(yArr.getInt(i));
    }
    return n;
  }

  /** {@code knn.predict(query[])} → majority-vote label (legacy 1-arg form). */
  public long predict(ChuckArray xArr) {
    if (trainX.isEmpty()) return 0L;
    double[] query = KNN.toDoubleArray(xArr);
    int[] indices = kNearest(query, k);
    return majorityVote(indices);
  }

  /**
   * {@code knn.predict(query[], k, prob[])} — fills prob[] with class probability (fraction of k
   * neighbors with each class index).
   */
  public long predict(ChuckArray xArr, long kk, ChuckArray probArr) {
    if (trainX.isEmpty()) return 0L;
    double[] query = KNN.toDoubleArray(xArr);
    int[] indices = kNearest(query, (int) kk);
    // count votes per class index (class labels assumed 0..probArr.size()-1)
    int nClasses = probArr.size();
    double[] votes = new double[nClasses];
    for (int idx : indices) {
      int cls = (int) trainY.get(idx).longValue();
      if (cls >= 0 && cls < nClasses) votes[cls]++;
    }
    for (int c = 0; c < nClasses; c++) probArr.setFloat(c, votes[c] / indices.length);
    return 1L;
  }

  /**
   * {@code knn.search(query[], k, indices[])} — fills indices[] with positions of k nearest
   * neighbors.
   */
  public long search(ChuckArray xArr, long kk, ChuckArray indicesArr) {
    if (trainX.isEmpty()) return 0L;
    double[] query = KNN.toDoubleArray(xArr);
    int[] indices = kNearest(query, (int) kk);
    for (int i = 0; i < indices.length && i < indicesArr.size(); i++)
      indicesArr.setInt(i, indices[i]);
    return indices.length;
  }

  /** {@code knn.search(input[], results[])} — fills results[] with k nearest labels (legacy). */
  public long search(ChuckArray xArr, ChuckArray out) {
    if (trainX.isEmpty()) return 0L;
    double[] query = KNN.toDoubleArray(xArr);
    int[] indices = kNearest(query, k);
    for (int i = 0; i < indices.length; i++) out.setFloat(i, (double) trainY.get(indices[i]));
    return indices.length;
  }

  private long majorityVote(int[] indices) {
    java.util.Map<Long, Integer> votes = new java.util.HashMap<>();
    for (int idx : indices) votes.merge(trainY.get(idx), 1, Integer::sum);
    return votes.entrySet().stream()
        .max(java.util.Map.Entry.comparingByValue())
        .map(java.util.Map.Entry::getKey)
        .orElse(0L);
  }

  private int[] kNearest(double[] query, int kk) {
    int n = trainX.size();
    double[] dists = new double[n];
    for (int i = 0; i < n; i++) dists[i] = weightedEuclidean(query, trainX.get(i));
    int count = Math.min(kk, n);
    int[] idxs = new int[count];
    boolean[] used = new boolean[n];
    for (int r = 0; r < count; r++) {
      int best = -1;
      for (int i = 0; i < n; i++) if (!used[i] && (best == -1 || dists[i] < dists[best])) best = i;
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
}
