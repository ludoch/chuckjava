package org.chuck.core.ai;

import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckObject;

import java.util.ArrayList;
import java.util.List;

/**
 * K-Nearest Neighbor — classification variant.
 * Returns the majority class among k nearest neighbors.
 */
public class KNN2 extends ChuckObject {

    private int k = 1;
    private final List<double[]> trainX = new ArrayList<>();
    private final List<Long>     trainY = new ArrayList<>();

    public long k(long val) { k = (int) Math.max(1, val); return k; }
    public long getK()       { return k; }

    /** train(input[][], labels[]) */
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

    /** predict(input[]) → label */
    public long predict(ChuckArray xArr) {
        if (trainX.isEmpty()) return 0L;
        double[] query = KNN.toDoubleArray(xArr);
        int[] indices = kNearest(query);
        // majority vote
        java.util.Map<Long, Integer> votes = new java.util.HashMap<>();
        for (int idx : indices) votes.merge(trainY.get(idx), 1, Integer::sum);
        return votes.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(0L);
    }

    /** search(input[], results[]) — fills results[] with k nearest labels */
    public long search(ChuckArray xArr, ChuckArray out) {
        if (trainX.isEmpty()) return 0L;
        double[] query = KNN.toDoubleArray(xArr);
        int[] indices = kNearest(query);
        for (int i = 0; i < indices.length; i++) out.setFloat(i, (double) trainY.get(indices[i]));
        return indices.length;
    }

    private int[] kNearest(double[] query) {
        int n = trainX.size();
        double[] dists = new double[n];
        for (int i = 0; i < n; i++) dists[i] = KNN.euclidean(query, trainX.get(i));
        int kk = Math.min(k, n);
        int[] idxs = new int[kk];
        boolean[] used = new boolean[n];
        for (int r = 0; r < kk; r++) {
            int best = -1;
            for (int i = 0; i < n; i++) if (!used[i] && (best == -1 || dists[i] < dists[best])) best = i;
            idxs[r] = best;
            used[best] = true;
        }
        return idxs;
    }
}
