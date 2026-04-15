package org.chuck.core.ai;

import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

/**
 * Principal Component Analysis — batch, power-iteration eigenvector solver. train(data[][]) —
 * computes principal components transform(input[], output[]) — projects input onto reduced space
 * explainedVariance(out[]) — fills out[] with explained variance ratios
 */
public class PCA extends ChuckObject {

  public PCA() {
    super(ChuckType.OBJECT);
  }

  private double[][] components; // [numComponents][inputDim]
  private double[] mean;
  private double[] explainedVar;
  private int numComponents = -1; // -1 = auto (all)

  public long numComponents(long n) {
    numComponents = (int) n;
    return n;
  }

  public long train(ChuckArray dataArr) {
    int n = dataArr.size();
    if (n == 0) return 0L;
    int D = ((ChuckArray) dataArr.getObject(0)).size();
    double[][] X = new double[n][D];
    for (int i = 0; i < n; i++) X[i] = KNN.toDoubleArray((ChuckArray) dataArr.getObject(i));

    // center
    mean = new double[D];
    for (double[] row : X) for (int d = 0; d < D; d++) mean[d] += row[d];
    for (int d = 0; d < D; d++) mean[d] /= n;
    for (double[] row : X) for (int d = 0; d < D; d++) row[d] -= mean[d];

    // covariance matrix (D x D, symmetric)
    double[][] cov = new double[D][D];
    for (double[] row : X)
      for (int i = 0; i < D; i++) for (int j = 0; j < D; j++) cov[i][j] += row[i] * row[j];
    for (int i = 0; i < D; i++) for (int j = 0; j < D; j++) cov[i][j] /= (n - 1);

    // power iteration to find top-k eigenvectors
    int k = (numComponents > 0) ? Math.min(numComponents, D) : D;
    components = new double[k][D];
    explainedVar = new double[k];
    double totalVar = 0;
    for (int i = 0; i < D; i++) totalVar += cov[i][i];

    // deflation: extract eigenvectors one by one
    double[][] mat = cov;
    for (int pc = 0; pc < k; pc++) {
      double[] v = powerIter(mat, D);
      components[pc] = v.clone();
      double eigenval = rayleigh(mat, v, D);
      explainedVar[pc] = totalVar > 0 ? eigenval / totalVar : 0;
      // deflate: mat -= eigenval * v * v^T
      for (int i = 0; i < D; i++) for (int j = 0; j < D; j++) mat[i][j] -= eigenval * v[i] * v[j];
    }
    return k;
  }

  /** project input[] onto PCA space → output[] */
  public long transform(ChuckArray inArr, ChuckArray outArr) {
    if (components == null) return 0L;
    double[] x = KNN.toDoubleArray(inArr);
    int D = mean.length, k = components.length;
    double[] centered = new double[D];
    for (int d = 0; d < D && d < x.length; d++) centered[d] = x[d] - mean[d];
    for (int pc = 0; pc < k; pc++) {
      double dot = 0;
      for (int d = 0; d < D; d++) dot += components[pc][d] * centered[d];
      outArr.setFloat(pc, dot);
    }
    return k;
  }

  public long explainedVariance(ChuckArray outArr) {
    if (explainedVar == null) return 0L;
    KNN.fillArray(outArr, explainedVar);
    return explainedVar.length;
  }

  private static double[] powerIter(double[][] mat, int D) {
    double[] v = new double[D];
    v[0] = 1;
    for (int iter = 0; iter < 200; iter++) {
      double[] next = new double[D];
      for (int i = 0; i < D; i++) for (int j = 0; j < D; j++) next[i] += mat[i][j] * v[j];
      double norm = 0;
      for (double x : next) norm += x * x;
      norm = Math.sqrt(norm);
      if (norm < 1e-12) break;
      for (int i = 0; i < D; i++) v[i] = next[i] / norm;
    }
    return v;
  }

  private static double rayleigh(double[][] mat, double[] v, int D) {
    double num = 0;
    for (int i = 0; i < D; i++) {
      double mv = 0;
      for (int j = 0; j < D; j++) mv += mat[i][j] * v[j];
      num += v[i] * mv;
    }
    return num;
  }

  // ── Static convenience API ─────────────────────────────────────────────────

  /**
   * {@code PCA.reduce(input[][], k, output[][])} — fit PCA on input and project to k dimensions.
   * Static convenience method (can also be called on an instance).
   */
  public static long reduce(ChuckArray inputArr, long k, ChuckArray outputArr) {
    PCA pca = new PCA();
    pca.numComponents(k);
    pca.train(inputArr);
    int n = inputArr.size();
    for (int i = 0; i < n && i < outputArr.size(); i++) {
      Object rowObj = outputArr.getObject(i);
      if (rowObj instanceof ChuckArray row) {
        ChuckArray inRow = (ChuckArray) inputArr.getObject(i);
        pca.transform(inRow, row);
      }
    }
    return n;
  }

  /**
   * {@code pca.transform(input[][], output[][])} — batch transform of 2D data (instance method).
   */
  public long transform(ChuckArray inputArr, ChuckArray outputArr) {
    if (components == null) return 0L;
    int n = inputArr.size();
    for (int i = 0; i < n && i < outputArr.size(); i++) {
      Object rowObj = outputArr.getObject(i);
      ChuckArray inRow = (ChuckArray) inputArr.getObject(i);
      if (rowObj instanceof ChuckArray row) {
        transform(inRow, row);
      } else {
        // outputArr is 1D — project single row
        transform(inRow, outputArr);
        break;
      }
    }
    return n;
  }
}
