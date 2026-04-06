package org.chuck.core.ai;

import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Support Vector Machine — binary and multiclass (one-vs-rest).
 * Implemented as a simple linear SVM trained with subgradient descent.
 * For real-world use a native library would be preferable, but this covers
 * the ChucK AI examples which use small feature vectors.
 */
public class SVM extends ChuckObject {

    public SVM() { super(ChuckType.OBJECT); }

    // One binary classifier per class (one-vs-rest)
    private double[][] weights;   // [numClasses][numFeatures+1]  (+1 for bias)
    private int numClasses = 2;
    private double learningRate = 0.01;
    private int maxIter = 1000;
    private double C = 1.0;  // regularization

    public long train(ChuckArray xArr, ChuckArray yArr) {
        int n = xArr.size();
        if (n == 0) return 0L;
        int d = ((ChuckArray) xArr.getObject(0)).size();
        // determine distinct classes
        java.util.Set<Long> classes = new java.util.LinkedHashSet<>();
        long[] labels = new long[n];
        for (int i = 0; i < n; i++) { labels[i] = yArr.getInt(i); classes.add(labels[i]); }
        numClasses = classes.size();
        Long[] classArr = classes.toArray(new Long[0]);

        weights = new double[numClasses][d + 1];
        double[][] X = new double[n][d];
        for (int i = 0; i < n; i++) X[i] = KNN.toDoubleArray((ChuckArray) xArr.getObject(i));

        // train one-vs-rest
        for (int c = 0; c < numClasses; c++) {
            long posClass = classArr[c];
            // subgradient descent
            for (int iter = 0; iter < maxIter; iter++) {
                for (int i = 0; i < n; i++) {
                    double y = (labels[i] == posClass) ? 1.0 : -1.0;
                    double score = dot(weights[c], X[i]);
                    if (y * score < 1.0) {
                        // hinge loss gradient
                        for (int j = 0; j < d; j++) weights[c][j] += learningRate * (y * X[i][j] - 2.0 / maxIter * weights[c][j]);
                        weights[c][d] += learningRate * y;
                    } else {
                        for (int j = 0; j < d; j++) weights[c][j] -= learningRate * 2.0 / maxIter * weights[c][j];
                    }
                }
            }
        }
        return 1L;
    }

    /** predict(input[]) → label (index into classes, 0-based) */
    public long predict(ChuckArray xArr) {
        if (weights == null) return 0L;
        double[] x = KNN.toDoubleArray(xArr);
        int best = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int c = 0; c < numClasses; c++) {
            double s = dot(weights[c], x);
            if (s > bestScore) { bestScore = s; best = c; }
        }
        return best;
    }

    public long save(String path) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path))) {
            oos.writeObject(weights);
            oos.writeInt(numClasses);
            return 1L;
        } catch (Exception e) { return 0L; }
    }

    public long load(String path) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path))) {
            weights = (double[][]) ois.readObject();
            numClasses = ois.readInt();
            return 1L;
        } catch (Exception e) { return 0L; }
    }

    private double dot(double[] w, double[] x) {
        double s = w[w.length - 1]; // bias
        for (int i = 0; i < x.length && i < w.length - 1; i++) s += w[i] * x[i];
        return s;
    }
}
