package org.chuck.core.ai;

import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

import java.util.Random;

/**
 * Multi-Layer Perceptron — fully-connected feedforward network.
 * Architecture: configurable input/hidden/output dimensions.
 * Training: mini-batch SGD with backpropagation (sigmoid activation, MSE loss).
 */
public class MLP extends ChuckObject {

    public MLP() { super(ChuckType.OBJECT); }

    private int inputSize  = 1;
    private int hiddenSize = 8;
    private int outputSize = 1;
    private int numLayers  = 1; // hidden layers (default 1)

    // weights[layer][j][i] — j=output neuron, i=input neuron (+bias at i=inputSize)
    private double[][][] weights;
    private double learningRate = 0.01;
    private int maxIter = 500;
    private String activation = "sigmoid";
    private static final Random rng = new Random();

    // --- architecture setters ---
    public long input(long n)  { inputSize  = (int) n; return n; }
    public long hidden(long n) { hiddenSize = (int) n; return n; }
    public long output(long n) { outputSize = (int) n; return n; }
    public long numLayers(long n) { numLayers = (int) Math.max(1, n); return n; }
    public long lr(double v)   { learningRate = v; return 0L; }
    public long epochs(long n) { maxIter = (int) n; return n; }
    public long activation(String s) { activation = s; return 0L; }

    private void initWeights() {
        // layer 0: input→hidden, layer 1..n-1: hidden→hidden, layer n: hidden→output
        int L = numLayers + 1;
        weights = new double[L][][];
        int[] sizes = new int[L + 1];
        sizes[0] = inputSize;
        for (int i = 1; i <= numLayers; i++) sizes[i] = hiddenSize;
        sizes[L] = outputSize;
        for (int l = 0; l < L; l++) {
            int in = sizes[l], out = sizes[l + 1];
            weights[l] = new double[out][in + 1]; // +1 bias
            double scale = Math.sqrt(2.0 / in);
            for (int j = 0; j < out; j++)
                for (int i = 0; i <= in; i++)
                    weights[l][j][i] = rng.nextGaussian() * scale;
        }
    }

    public long train(ChuckArray xArr, ChuckArray yArr) {
        int n = xArr.size();
        if (n == 0) return 0L;
        if (weights == null) initWeights();
        int L = weights.length;
        for (int epoch = 0; epoch < maxIter; epoch++) {
            for (int s = 0; s < n; s++) {
                double[] x = KNN.toDoubleArray((ChuckArray) xArr.getObject(s));
                double[] y = KNN.toDoubleArray((ChuckArray) yArr.getObject(s));
                // forward pass
                double[][] acts = forward(x);
                // backward pass
                double[][] deltas = new double[L][];
                // output layer delta
                double[] out = acts[L];
                deltas[L - 1] = new double[out.length];
                for (int j = 0; j < out.length; j++)
                    deltas[L - 1][j] = (out[j] - (j < y.length ? y[j] : 0)) * actDeriv(out[j]);
                // hidden layer deltas
                for (int l = L - 2; l >= 0; l--) {
                    deltas[l] = new double[weights[l].length];
                    for (int j = 0; j < weights[l].length; j++) {
                        double sum = 0;
                        for (int k = 0; k < deltas[l + 1].length; k++) sum += weights[l + 1][k][j] * deltas[l + 1][k];
                        deltas[l][j] = sum * actDeriv(acts[l + 1][j]);
                    }
                }
                // weight update
                for (int l = 0; l < L; l++) {
                    double[] in = acts[l];
                    for (int j = 0; j < weights[l].length; j++) {
                        for (int i = 0; i < in.length; i++) weights[l][j][i] -= learningRate * deltas[l][j] * in[i];
                        weights[l][j][in.length] -= learningRate * deltas[l][j]; // bias
                    }
                }
            }
        }
        return 1L;
    }

    public long predict(ChuckArray xArr, ChuckArray outArr) {
        if (weights == null) initWeights();
        double[] x = KNN.toDoubleArray(xArr);
        double[][] acts = forward(x);
        double[] result = acts[weights.length];
        KNN.fillArray(outArr, result);
        return 1L;
    }

    // returns activations at every layer (index 0 = input, index L = output)
    private double[][] forward(double[] x) {
        int L = weights.length;
        double[][] acts = new double[L + 1][];
        acts[0] = x;
        for (int l = 0; l < L; l++) {
            double[] prev = acts[l];
            acts[l + 1] = new double[weights[l].length];
            for (int j = 0; j < weights[l].length; j++) {
                double z = weights[l][j][prev.length]; // bias
                for (int i = 0; i < prev.length; i++) z += weights[l][j][i] * prev[i];
                acts[l + 1][j] = l < L - 1 ? act(z) : z; // linear output
            }
        }
        return acts;
    }

    private double act(double z) {
        return switch (activation) {
            case "relu"  -> Math.max(0, z);
            case "tanh"  -> Math.tanh(z);
            default      -> 1.0 / (1.0 + Math.exp(-z)); // sigmoid
        };
    }

    private double actDeriv(double a) {
        return switch (activation) {
            case "relu"  -> a > 0 ? 1.0 : 0.0;
            case "tanh"  -> 1 - a * a;
            default      -> a * (1 - a); // sigmoid
        };
    }
}
