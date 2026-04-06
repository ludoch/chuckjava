package org.chuck.core.ai;

import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

import java.util.Random;

/**
 * Hidden Markov Model — discrete-output, trained via Baum-Welch EM.
 * generate(output[]) — samples a sequence of length output.size()
 * viterbi(obs[][], path[]) — most likely state sequence
 * forward(obs[][]) — returns log-probability of observations
 *
 * States and observations are both represented as double arrays (feature vectors)
 * with Gaussian emission probabilities.
 */
public class HMM extends ChuckObject {

    public HMM() { super(ChuckType.OBJECT); }

    private int numStates  = 4;
    private int maxIter    = 100;
    private static final Random rng = new Random();

    // Model parameters (initialized lazily)
    private double[] pi;      // initial state probs [S]
    private double[][] A;     // transition [S][S]
    private double[][] mu;    // emission mean [S][D]
    private double[][] sigma; // emission std  [S][D]

    public long numStates(long n) { numStates = (int) Math.max(1, n); return n; }
    public long maxIter(long n)   { maxIter   = (int) n; return n; }

    /** train(observations[T][D]) — Baum-Welch on a single sequence */
    public long train(ChuckArray obsArr) {
        int T = obsArr.size();
        if (T == 0) return 0L;
        int D = ((ChuckArray) obsArr.getObject(0)).size();
        double[][] obs = toMatrix(obsArr, T, D);
        int S = numStates;

        // random init
        pi    = randomStochastic(S);
        A     = new double[S][S];
        mu    = new double[S][D];
        sigma = new double[S][D];
        for (int i = 0; i < S; i++) A[i] = randomStochastic(S);
        for (int i = 0; i < S; i++) for (int d = 0; d < D; d++) {
            mu[i][d]    = rng.nextGaussian();
            sigma[i][d] = 1.0;
        }

        // Baum-Welch
        for (int iter = 0; iter < maxIter; iter++) {
            double[][] alpha = forward(obs, T, S, D);
            double[][] beta  = backward(obs, T, S, D);
            // gamma[t][i] = P(state=i at t | obs)
            double[][] gamma = new double[T][S];
            for (int t = 0; t < T; t++) {
                double z = 0; for (int i = 0; i < S; i++) z += alpha[t][i] * beta[t][i];
                if (z == 0) z = 1e-300;
                for (int i = 0; i < S; i++) gamma[t][i] = alpha[t][i] * beta[t][i] / z;
            }
            // xi[t][i][j]
            double[][][] xi = new double[T - 1][S][S];
            for (int t = 0; t < T - 1; t++) {
                double z = 0;
                for (int i = 0; i < S; i++) for (int j = 0; j < S; j++)
                    z += alpha[t][i] * A[i][j] * emit(obs[t + 1], i, D) * beta[t + 1][j];
                if (z == 0) z = 1e-300;
                for (int i = 0; i < S; i++) for (int j = 0; j < S; j++)
                    xi[t][i][j] = alpha[t][i] * A[i][j] * emit(obs[t + 1], i, D) * beta[t + 1][j] / z;
            }
            // re-estimate
            for (int i = 0; i < S; i++) pi[i] = gamma[0][i];
            for (int i = 0; i < S; i++) {
                double gi = 0; for (int t = 0; t < T - 1; t++) gi += gamma[t][i];
                if (gi == 0) gi = 1e-300;
                for (int j = 0; j < S; j++) {
                    double xij = 0; for (int t = 0; t < T - 1; t++) xij += xi[t][i][j];
                    A[i][j] = xij / gi;
                }
            }
            for (int i = 0; i < S; i++) {
                double gi = 0; for (int t = 0; t < T; t++) gi += gamma[t][i];
                if (gi == 0) gi = 1e-300;
                for (int d = 0; d < D; d++) {
                    double m = 0; for (int t = 0; t < T; t++) m += gamma[t][i] * obs[t][d]; mu[i][d] = m / gi;
                    double v = 0; for (int t = 0; t < T; t++) { double e = obs[t][d] - mu[i][d]; v += gamma[t][i] * e * e; }
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
            for (int d = 0; d < D; d++) row.setFloat(d, mu[state][d] + sigma[state][d] * rng.nextGaussian());
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
        double z = 0; for (int i = 0; i < S; i++) z += alpha[T - 1][i];
        return Math.log(Math.max(z, 1e-300));
    }

    // -------- internal --------

    private double[][] forward(double[][] obs, int T, int S, int D) {
        double[][] alpha = new double[T][S];
        for (int i = 0; i < S; i++) alpha[0][i] = pi[i] * emit(obs[0], i, D);
        for (int t = 1; t < T; t++) for (int j = 0; j < S; j++) {
            double s = 0; for (int i = 0; i < S; i++) s += alpha[t - 1][i] * A[i][j];
            alpha[t][j] = s * emit(obs[t], j, D);
        }
        return alpha;
    }

    private double[][] backward(double[][] obs, int T, int S, int D) {
        double[][] beta = new double[T][S];
        for (int i = 0; i < S; i++) beta[T - 1][i] = 1;
        for (int t = T - 2; t >= 0; t--) for (int i = 0; i < S; i++) {
            double s = 0; for (int j = 0; j < S; j++) s += A[i][j] * emit(obs[t + 1], j, D) * beta[t + 1][j];
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
        double s = 0; for (int i = 0; i < n; i++) { v[i] = rng.nextDouble() + 0.1; s += v[i]; }
        for (int i = 0; i < n; i++) v[i] /= s;
        return v;
    }

    private static int sampleCat(double[] p) {
        double r = rng.nextDouble(), cum = 0;
        for (int i = 0; i < p.length; i++) { cum += p[i]; if (r < cum) return i; }
        return p.length - 1;
    }

    private static double[][] toMatrix(ChuckArray arr, int T, int D) {
        double[][] m = new double[T][D];
        for (int t = 0; t < T; t++) m[t] = KNN.toDoubleArray((ChuckArray) arr.getObject(t));
        return m;
    }
}
