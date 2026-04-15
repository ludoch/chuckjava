package org.chuck.core.ai;

import java.util.Random;
import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

/**
 * Hidden Markov Model — discrete-output, trained via Baum-Welch EM. generate(output[]) — samples a
 * sequence of length output.size() viterbi(obs[][], path[]) — most likely state sequence
 * forward(obs[][]) — returns log-probability of observations
 *
 * <p>States and observations are both represented as double arrays (feature vectors) with Gaussian
 * emission probabilities.
 */
public class HMM extends ChuckObject {

  public HMM() {
    super(ChuckType.OBJECT);
  }

  private int numStates = 4;
  private int maxIter = 100;
  private static final Random rng = new Random();

  // Model parameters — continuous Gaussian HMM
  private double[] pi; // initial state probs [S]
  private double[][] A; // transition [S][S]
  private double[][] mu; // emission mean [S][D]
  private double[][] sigma; // emission std  [S][D]

  // Discrete HMM parameters (set by load() or discrete train())
  private double[][] B; // emission probability B[state][obs_symbol]
  private boolean isDiscrete = false;
  private int numSymbols = 0; // vocabulary size

  public long numStates(long n) {
    numStates = (int) Math.max(1, n);
    return n;
  }

  public long maxIter(long n) {
    maxIter = (int) n;
    return n;
  }

  // ── Discrete HMM API (ChucK ChAI examples) ────────────────────────────────

  /**
   * {@code hmm.load(initial[], transition[][], emission[][])} — directly set discrete HMM
   * parameters.
   */
  public long load(ChuckArray initialArr, ChuckArray transitionArr, ChuckArray emissionArr) {
    int S = initialArr.size();
    numStates = S;
    pi = new double[S];
    for (int i = 0; i < S; i++) pi[i] = initialArr.getFloat(i);

    A = new double[S][S];
    for (int i = 0; i < S; i++) {
      ChuckArray row = (ChuckArray) transitionArr.getObject(i);
      for (int j = 0; j < S; j++) A[i][j] = row.getFloat(j);
    }

    numSymbols = ((ChuckArray) emissionArr.getObject(0)).size();
    B = new double[S][numSymbols];
    for (int i = 0; i < S; i++) {
      ChuckArray row = (ChuckArray) emissionArr.getObject(i);
      for (int k = 0; k < numSymbols; k++) B[i][k] = row.getFloat(k);
    }
    isDiscrete = true;
    return 1L;
  }

  /**
   * {@code hmm.train(numHidden, numObs, observations[])} — Baum-Welch for discrete-observation HMM.
   * observations[] is a 1D int array of symbol indices 0..numObs-1.
   */
  public long train(long numHiddenL, long numObsL, ChuckArray obsArr) {
    int S = (int) numHiddenL;
    int M = (int) numObsL;
    numStates = S;
    numSymbols = M;
    isDiscrete = true;

    int T = obsArr.size();
    int[] obs = new int[T];
    for (int t = 0; t < T; t++) obs[t] = (int) obsArr.getInt(t);

    // random init (uniform + small noise)
    pi = randomStochastic(S);
    A = new double[S][S];
    B = new double[S][M];
    for (int i = 0; i < S; i++) A[i] = randomStochastic(S);
    for (int i = 0; i < S; i++) B[i] = randomStochastic(M);

    // Baum-Welch for discrete HMM
    for (int iter = 0; iter < maxIter; iter++) {
      // forward
      double[][] alpha = new double[T][S];
      for (int i = 0; i < S; i++) alpha[0][i] = pi[i] * B[i][obs[0]];
      normalize(alpha[0]);
      for (int t = 1; t < T; t++) {
        for (int j = 0; j < S; j++) {
          double s = 0;
          for (int i = 0; i < S; i++) s += alpha[t - 1][i] * A[i][j];
          alpha[t][j] = s * B[j][obs[t]];
        }
        normalize(alpha[t]);
      }
      // backward
      double[][] beta = new double[T][S];
      for (int i = 0; i < S; i++) beta[T - 1][i] = 1.0;
      for (int t = T - 2; t >= 0; t--) {
        for (int i = 0; i < S; i++) {
          double s = 0;
          for (int j = 0; j < S; j++) s += A[i][j] * B[j][obs[t + 1]] * beta[t + 1][j];
          beta[t][i] = s;
        }
        normalize(beta[t]);
      }
      // gamma and xi re-estimation
      double[] newPi = new double[S];
      double[][] newA = new double[S][S];
      double[][] newB = new double[S][M];
      for (int t = 0; t < T; t++) {
        double z = 0;
        for (int i = 0; i < S; i++) z += alpha[t][i] * beta[t][i];
        if (z < 1e-300) z = 1e-300;
        for (int i = 0; i < S; i++) {
          double g = alpha[t][i] * beta[t][i] / z;
          if (t == 0) newPi[i] += g;
          newB[i][obs[t]] += g;
          if (t < T - 1) {
            for (int j = 0; j < S; j++) {
              newA[i][j] += alpha[t][i] * A[i][j] * B[j][obs[t + 1]] * beta[t + 1][j] / z;
            }
          }
        }
      }
      // normalize
      normalize(newPi);
      pi = newPi;
      for (int i = 0; i < S; i++) {
        normalize(newA[i]);
        A[i] = newA[i];
        normalize(newB[i]);
        B[i] = newB[i];
      }
    }
    return 1L;
  }

  /**
   * {@code hmm.generate(length, results[])} — generate a sequence of {@code length} discrete
   * observation symbols into the pre-allocated int[] results.
   */
  public long generate(long length, ChuckArray outArr) {
    if (pi == null) return 0L;
    int T = (int) length;
    int state = sampleCat(pi);
    for (int t = 0; t < T && t < outArr.size(); t++) {
      if (isDiscrete) {
        outArr.setInt(t, sampleCat(B[state]));
      } else {
        // continuous: put the mean
        outArr.setFloat(t, mu != null ? mu[state][0] : 0);
      }
      state = sampleCat(A[state]);
    }
    return T;
  }

  private static void normalize(double[] v) {
    double s = 0;
    for (double x : v) s += x;
    if (s < 1e-300) {
      for (int i = 0; i < v.length; i++) v[i] = 1.0 / v.length;
    } else {
      for (int i = 0; i < v.length; i++) v[i] /= s;
    }
  }

  /** train(observations[T][D]) — Baum-Welch on a single sequence */
  public long train(ChuckArray obsArr) {
    int T = obsArr.size();
    if (T == 0) return 0L;
    int D = ((ChuckArray) obsArr.getObject(0)).size();
    double[][] obs = toMatrix(obsArr, T, D);
    int S = numStates;

    // random init
    pi = randomStochastic(S);
    A = new double[S][S];
    mu = new double[S][D];
    sigma = new double[S][D];
    for (int i = 0; i < S; i++) A[i] = randomStochastic(S);
    for (int i = 0; i < S; i++)
      for (int d = 0; d < D; d++) {
        mu[i][d] = rng.nextGaussian();
        sigma[i][d] = 1.0;
      }

    // Baum-Welch
    for (int iter = 0; iter < maxIter; iter++) {
      double[][] alpha = forward(obs, T, S, D);
      double[][] beta = backward(obs, T, S, D);
      // gamma[t][i] = P(state=i at t | obs)
      double[][] gamma = new double[T][S];
      for (int t = 0; t < T; t++) {
        double z = 0;
        for (int i = 0; i < S; i++) z += alpha[t][i] * beta[t][i];
        if (z == 0) z = 1e-300;
        for (int i = 0; i < S; i++) gamma[t][i] = alpha[t][i] * beta[t][i] / z;
      }
      // xi[t][i][j]
      double[][][] xi = new double[T - 1][S][S];
      for (int t = 0; t < T - 1; t++) {
        double z = 0;
        for (int i = 0; i < S; i++)
          for (int j = 0; j < S; j++)
            z += alpha[t][i] * A[i][j] * emit(obs[t + 1], i, D) * beta[t + 1][j];
        if (z == 0) z = 1e-300;
        for (int i = 0; i < S; i++)
          for (int j = 0; j < S; j++)
            xi[t][i][j] = alpha[t][i] * A[i][j] * emit(obs[t + 1], i, D) * beta[t + 1][j] / z;
      }
      // re-estimate
      for (int i = 0; i < S; i++) pi[i] = gamma[0][i];
      for (int i = 0; i < S; i++) {
        double gi = 0;
        for (int t = 0; t < T - 1; t++) gi += gamma[t][i];
        if (gi == 0) gi = 1e-300;
        for (int j = 0; j < S; j++) {
          double xij = 0;
          for (int t = 0; t < T - 1; t++) xij += xi[t][i][j];
          A[i][j] = xij / gi;
        }
      }
      for (int i = 0; i < S; i++) {
        double gi = 0;
        for (int t = 0; t < T; t++) gi += gamma[t][i];
        if (gi == 0) gi = 1e-300;
        for (int d = 0; d < D; d++) {
          double m = 0;
          for (int t = 0; t < T; t++) m += gamma[t][i] * obs[t][d];
          mu[i][d] = m / gi;
          double v = 0;
          for (int t = 0; t < T; t++) {
            double e = obs[t][d] - mu[i][d];
            v += gamma[t][i] * e * e;
          }
          sigma[i][d] = Math.sqrt(v / gi + 1e-6);
        }
      }
    }
    return 1L;
  }

  /** generate(output[T][D]) — fills output with sampled sequence */
  public long generate(ChuckArray outArr) {
    if (pi == null) return 0L;
    int T = outArr.size();
    int D = mu[0].length;
    int S = numStates;
    int state = sampleCat(pi);
    for (int t = 0; t < T; t++) {
      ChuckArray row = (ChuckArray) outArr.getObject(t);
      for (int d = 0; d < D; d++)
        row.setFloat(d, mu[state][d] + sigma[state][d] * rng.nextGaussian());
      state = sampleCat(A[state]);
    }
    return T;
  }

  /** forward(obs[][], ignored — returns log-likelihood) */
  public double logLikelihood(ChuckArray obsArr) {
    if (pi == null) return 0.0;
    int T = obsArr.size(), D = ((ChuckArray) obsArr.getObject(0)).size(), S = numStates;
    double[][] obs = toMatrix(obsArr, T, D);
    double[][] alpha = forward(obs, T, S, D);
    double z = 0;
    for (int i = 0; i < S; i++) z += alpha[T - 1][i];
    return Math.log(Math.max(z, 1e-300));
  }

  // -------- internal --------

  private double[][] forward(double[][] obs, int T, int S, int D) {
    double[][] alpha = new double[T][S];
    for (int i = 0; i < S; i++) alpha[0][i] = pi[i] * emit(obs[0], i, D);
    for (int t = 1; t < T; t++)
      for (int j = 0; j < S; j++) {
        double s = 0;
        for (int i = 0; i < S; i++) s += alpha[t - 1][i] * A[i][j];
        alpha[t][j] = s * emit(obs[t], j, D);
      }
    return alpha;
  }

  private double[][] backward(double[][] obs, int T, int S, int D) {
    double[][] beta = new double[T][S];
    for (int i = 0; i < S; i++) beta[T - 1][i] = 1;
    for (int t = T - 2; t >= 0; t--)
      for (int i = 0; i < S; i++) {
        double s = 0;
        for (int j = 0; j < S; j++) s += A[i][j] * emit(obs[t + 1], j, D) * beta[t + 1][j];
        beta[t][i] = s;
      }
    return beta;
  }

  private double emit(double[] x, int state, int D) {
    double p = 1.0;
    for (int d = 0; d < Math.min(D, x.length); d++) {
      double e = (x[d] - mu[state][d]) / sigma[state][d];
      p *= Math.exp(-0.5 * e * e) / (sigma[state][d] * Math.sqrt(2 * Math.PI));
    }
    return Math.max(p, 1e-300);
  }

  private static double[] randomStochastic(int n) {
    double[] v = new double[n];
    double s = 0;
    for (int i = 0; i < n; i++) {
      v[i] = rng.nextDouble() + 0.1;
      s += v[i];
    }
    for (int i = 0; i < n; i++) v[i] /= s;
    return v;
  }

  private static int sampleCat(double[] p) {
    double r = rng.nextDouble(), cum = 0;
    for (int i = 0; i < p.length; i++) {
      cum += p[i];
      if (r < cum) return i;
    }
    return p.length - 1;
  }

  private static double[][] toMatrix(ChuckArray arr, int T, int D) {
    double[][] m = new double[T][D];
    for (int t = 0; t < T; t++) m[t] = KNN.toDoubleArray((ChuckArray) arr.getObject(t));
    return m;
  }
}
