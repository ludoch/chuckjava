package org.chuck.core.ai;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.Random;
import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

/**
 * Multi-Layer Perceptron — fully-connected feedforward network.
 *
 * <p>Matches the ChucK ChAI MLP API: {@code init(nodesPerLayer[])}, {@code train(X[][], Y[][], lr,
 * epochs)}, {@code predict(x[], y[])}, step-by-step {@code forward}/{@code backprop}, inspection
 * ({@code getActivations}/{@code getWeights}/{@code getBiases}/{@code getGradients}), and model
 * persistence ({@code save}/{@code load}).
 */
public class MLP extends ChuckObject {

  // ── Activation type constants (mirror AI.Sigmoid etc.) ────────────────────
  public static final int ACT_SIGMOID = 0;
  public static final int ACT_TANH = 1;
  public static final int ACT_RELU = 2;
  public static final int ACT_LINEAR = 3;
  public static final int ACT_SOFTMAX = 4;

  public MLP() {
    super(ChuckType.OBJECT);
  }

  // ── Architecture ──────────────────────────────────────────────────────────
  private int[] layerSizes; // sizes[0]=input, sizes[L]=output
  private int[] layerActivations; // activation per layer (0..L-1 are hidden+output)

  // weights[l][j][i]: layer l, output neuron j, input neuron i (+bias at i=sizes[l])
  private double[][][] weights;
  // last forward pass activations (size = layerSizes.length)
  private double[][] lastActs;
  // last backprop deltas (size = num weight layers)
  private double[][] lastDeltas;

  // ── Legacy single-value setters (for Wekinator compatibility) ─────────────
  private int inputSize = 1;
  private int hiddenSize = 8;
  private int outputSize = 1;
  private int numHiddenLayers = 1;
  private double learningRate = 0.01;
  private int maxIter = 500;

  private static final Random rng = new Random();

  // ── Architecture initialisation ───────────────────────────────────────────

  /** {@code mlp.init(nodesPerLayer)} — full architecture from int[]. */
  public long init(ChuckArray nodesArr) {
    return initWithActivation(nodesArr, -1, null);
  }

  /** {@code mlp.init(nodesPerLayer, AI.Sigmoid)} — uniform activation. */
  public long init(ChuckArray nodesArr, long activation) {
    return initWithActivation(nodesArr, (int) activation, null);
  }

  /** {@code mlp.init(nodesPerLayer, activationPerLayer[])} — per-layer activations. */
  public long init(ChuckArray nodesArr, ChuckArray activArr) {
    return initWithActivation(nodesArr, -1, activArr);
  }

  private long initWithActivation(ChuckArray nodesArr, int uniformAct, ChuckArray activArr) {
    int n = nodesArr.size();
    if (n < 2) return 0L;
    layerSizes = new int[n];
    for (int i = 0; i < n; i++) layerSizes[i] = (int) nodesArr.getInt(i);

    int numWeightLayers = n - 1;
    layerActivations = new int[numWeightLayers];
    for (int l = 0; l < numWeightLayers; l++) {
      if (activArr != null && l < activArr.size()) {
        layerActivations[l] = (int) activArr.getInt(l);
      } else if (uniformAct >= 0) {
        layerActivations[l] = (l < numWeightLayers - 1) ? uniformAct : ACT_LINEAR;
      } else {
        // default: sigmoid for hidden, linear for output
        layerActivations[l] = (l < numWeightLayers - 1) ? ACT_SIGMOID : ACT_LINEAR;
      }
    }
    initWeightsFromSizes();
    return 1L;
  }

  private void ensureInitFromLegacy() {
    if (layerSizes != null) return; // already init'd via init()
    // build from legacy fields
    int n = 2 + numHiddenLayers;
    layerSizes = new int[n];
    layerSizes[0] = inputSize;
    for (int i = 1; i <= numHiddenLayers; i++) layerSizes[i] = hiddenSize;
    layerSizes[n - 1] = outputSize;

    int numWeightLayers = n - 1;
    layerActivations = new int[numWeightLayers];
    for (int l = 0; l < numWeightLayers; l++) {
      layerActivations[l] = (l < numWeightLayers - 1) ? ACT_SIGMOID : ACT_LINEAR;
    }
    initWeightsFromSizes();
  }

  private void initWeightsFromSizes() {
    int L = layerSizes.length - 1;
    weights = new double[L][][];
    for (int l = 0; l < L; l++) {
      int in = layerSizes[l], out = layerSizes[l + 1];
      weights[l] = new double[out][in + 1]; // +1 bias
      double scale = Math.sqrt(2.0 / in);
      for (int j = 0; j < out; j++)
        for (int i = 0; i <= in; i++) weights[l][j][i] = rng.nextGaussian() * scale;
    }
    lastActs = null;
    lastDeltas = null;
  }

  // ── Legacy architecture setters (Wekinator compat) ────────────────────────
  public long input(long n) {
    inputSize = (int) n;
    layerSizes = null;
    return n;
  }

  public long hidden(long n) {
    hiddenSize = (int) n;
    layerSizes = null;
    return n;
  }

  public long output(long n) {
    outputSize = (int) n;
    layerSizes = null;
    return n;
  }

  public long numLayers(long n) {
    numHiddenLayers = (int) Math.max(1, n);
    layerSizes = null;
    return n;
  }

  public long inputSize(int n) {
    inputSize = Math.max(1, n);
    layerSizes = null;
    return inputSize;
  }

  public long hiddenSize(int n) {
    hiddenSize = Math.max(1, n);
    layerSizes = null;
    return hiddenSize;
  }

  public long outputSize(int n) {
    outputSize = Math.max(1, n);
    layerSizes = null;
    return outputSize;
  }

  public long lr(double v) {
    learningRate = v;
    return 0L;
  }

  public long learningRate(double v) {
    learningRate = v;
    return 0L;
  }

  public double getLearningRate() {
    return learningRate;
  }

  public long epochs(long n) {
    maxIter = (int) n;
    return n;
  }

  public long activation(String s) {
    // legacy single-string activation — store and apply on next init
    int code =
        switch (s) {
          case "relu", "ReLU" -> ACT_RELU;
          case "tanh", "Tanh" -> ACT_TANH;
          case "linear", "Linear" -> ACT_LINEAR;
          default -> ACT_SIGMOID;
        };
    if (layerActivations != null) {
      for (int l = 0; l < layerActivations.length - 1; l++) layerActivations[l] = code;
    }
    return 0L;
  }

  // ── Training ──────────────────────────────────────────────────────────────

  /** {@code mlp.train(X, Y)} — train with stored lr/epochs. */
  public long train(ChuckArray xArr, ChuckArray yArr) {
    return trainInternal(xArr, yArr, learningRate, maxIter);
  }

  /** {@code mlp.train(X, Y, learningRate, epochs)} */
  public long train(ChuckArray xArr, ChuckArray yArr, double lr, long epochs) {
    return trainInternal(xArr, yArr, lr, (int) epochs);
  }

  private long trainInternal(ChuckArray xArr, ChuckArray yArr, double lr, int nEpochs) {
    int n = xArr.size();
    if (n == 0) return 0L;
    ensureInitFromLegacy();

    for (int epoch = 0; epoch < nEpochs; epoch++) {
      for (int s = 0; s < n; s++) {
        double[] x = toDoubleArray((ChuckArray) xArr.getObject(s));
        double[] y = toDoubleArray((ChuckArray) yArr.getObject(s));
        forwardInternal(x);
        backpropInternal(y, lr);
      }
    }
    return 1L;
  }

  /** {@code MLP.shuffle(X, Y)} — static shuffle of parallel jagged arrays. */
  public static long shuffle(ChuckArray xArr, ChuckArray yArr) {
    int n = xArr.size();
    for (int i = n - 1; i > 0; i--) {
      int j = rng.nextInt(i + 1);
      Object tx = xArr.getObject(i), ty = yArr.getObject(i);
      xArr.setObject(i, xArr.getObject(j));
      yArr.setObject(i, yArr.getObject(j));
      xArr.setObject(j, tx);
      yArr.setObject(j, ty);
    }
    return 0L;
  }

  // ── Step-by-step training (manual mode) ───────────────────────────────────

  /** {@code mlp.forward(x[])} — forward pass only; stores activations. */
  public long forward(ChuckArray xArr) {
    ensureInitFromLegacy();
    double[] x = toDoubleArray(xArr);
    forwardInternal(x);
    return 1L;
  }

  /** {@code mlp.backprop(y[], lr)} — backprop step using last forward activations. */
  public long backprop(ChuckArray yArr, double lr) {
    if (lastActs == null) return 0L;
    double[] y = toDoubleArray(yArr);
    backpropInternal(y, lr);
    return 1L;
  }

  // ── Prediction ────────────────────────────────────────────────────────────

  public long predict(ChuckArray xArr, ChuckArray outArr) {
    ensureInitFromLegacy();
    double[] x = toDoubleArray(xArr);
    forwardInternal(x);
    double[] result = lastActs[lastActs.length - 1];
    fillArray(outArr, result);
    return 1L;
  }

  // ── Inspection ────────────────────────────────────────────────────────────

  /** {@code mlp.getActivations(layer, result[])} */
  public long getActivations(long layer, ChuckArray result) {
    if (lastActs == null || layer < 0 || layer >= lastActs.length) return 0L;
    fillArray(result, lastActs[(int) layer]);
    return 1L;
  }

  /** {@code mlp.getWeights(layer, result[][])} — fills a 2D ChuckArray */
  public long getWeights(long layer, ChuckArray result) {
    if (weights == null || layer < 0 || layer >= weights.length) return 0L;
    double[][] w = weights[(int) layer];
    for (int i = 0; i < w.length && i < result.size(); i++) {
      Object row = result.getObject(i);
      if (row instanceof ChuckArray ra) {
        for (int j = 0; j < w[i].length - 1 && j < ra.size(); j++) ra.setFloat(j, w[i][j]);
      }
    }
    return 1L;
  }

  /** {@code mlp.getBiases(layer, result[])} */
  public long getBiases(long layer, ChuckArray result) {
    if (weights == null || layer < 0 || layer >= weights.length) return 0L;
    double[][] w = weights[(int) layer];
    for (int j = 0; j < w.length && j < result.size(); j++)
      result.setFloat(j, w[j][layerSizes[(int) layer]]);
    return 1L;
  }

  /** {@code mlp.getGradients(layer, result[])} */
  public long getGradients(long layer, ChuckArray result) {
    if (lastDeltas == null || layer < 0 || layer >= lastDeltas.length) return 0L;
    fillArray(result, lastDeltas[(int) layer]);
    return 1L;
  }

  // ── Model persistence ─────────────────────────────────────────────────────

  /** {@code mlp.save(path)} */
  public long save(String path) {
    if (weights == null) return 0L;
    try (PrintWriter pw = new PrintWriter(path)) {
      pw.println("chuck-mlp-v1");
      pw.println(layerSizes.length);
      for (int s : layerSizes) pw.print(s + " ");
      pw.println();
      for (int a : layerActivations) pw.print(a + " ");
      pw.println();
      for (double[][] layer : weights) {
        for (double[] row : layer) {
          for (double v : row) pw.print(v + " ");
          pw.println();
        }
      }
    } catch (Exception e) {
      return 0L;
    }
    return 1L;
  }

  /** {@code mlp.load(path)} */
  public long load(String path) {
    try (BufferedReader br = new BufferedReader(new FileReader(path))) {
      String header = br.readLine();
      if (header == null || !header.startsWith("chuck-mlp")) return 0L;
      int n = Integer.parseInt(br.readLine().trim());
      layerSizes = new int[n];
      String[] szTokens = br.readLine().trim().split("\\s+");
      for (int i = 0; i < n; i++) layerSizes[i] = Integer.parseInt(szTokens[i]);
      layerActivations = new int[n - 1];
      String[] acTokens = br.readLine().trim().split("\\s+");
      for (int i = 0; i < n - 1; i++) layerActivations[i] = Integer.parseInt(acTokens[i]);

      int L = n - 1;
      weights = new double[L][][];
      for (int l = 0; l < L; l++) {
        int out = layerSizes[l + 1], in = layerSizes[l];
        weights[l] = new double[out][in + 1];
        for (int j = 0; j < out; j++) {
          String[] row = br.readLine().trim().split("\\s+");
          for (int i = 0; i <= in; i++) weights[l][j][i] = Double.parseDouble(row[i]);
        }
      }
    } catch (Exception e) {
      return 0L;
    }
    return 1L;
  }

  // ── Internal forward/backward ─────────────────────────────────────────────

  private void forwardInternal(double[] x) {
    int L = weights.length;
    lastActs = new double[L + 1][];
    lastActs[0] = x;
    for (int l = 0; l < L; l++) {
      double[] prev = lastActs[l];
      lastActs[l + 1] = new double[weights[l].length];
      int actType = layerActivations[l];
      for (int j = 0; j < weights[l].length; j++) {
        double z = weights[l][j][prev.length]; // bias
        for (int i = 0; i < prev.length; i++) z += weights[l][j][i] * prev[i];
        lastActs[l + 1][j] = applyActivation(z, actType, l == L - 1);
      }
      // softmax post-processing
      if (actType == ACT_SOFTMAX) {
        double max = Double.NEGATIVE_INFINITY;
        for (double v : lastActs[l + 1]) max = Math.max(max, v);
        double sum = 0;
        for (int j = 0; j < lastActs[l + 1].length; j++) {
          lastActs[l + 1][j] = Math.exp(lastActs[l + 1][j] - max);
          sum += lastActs[l + 1][j];
        }
        for (int j = 0; j < lastActs[l + 1].length; j++) lastActs[l + 1][j] /= sum;
      }
    }
  }

  private void backpropInternal(double[] y, double lr) {
    int L = weights.length;
    double[][] deltas = new double[L][];

    // output layer delta (linear: derivative = 1.0)
    double[] out = lastActs[L];
    deltas[L - 1] = new double[out.length];
    for (int j = 0; j < out.length; j++) deltas[L - 1][j] = (out[j] - (j < y.length ? y[j] : 0));

    // hidden layer deltas
    for (int l = L - 2; l >= 0; l--) {
      deltas[l] = new double[weights[l].length];
      for (int j = 0; j < weights[l].length; j++) {
        double sum = 0;
        for (int k = 0; k < deltas[l + 1].length; k++)
          sum += weights[l + 1][k][j] * deltas[l + 1][k];
        deltas[l][j] = sum * activationDeriv(lastActs[l + 1][j], layerActivations[l]);
      }
    }

    // weight update
    for (int l = 0; l < L; l++) {
      double[] in = lastActs[l];
      for (int j = 0; j < weights[l].length; j++) {
        for (int i = 0; i < in.length; i++) weights[l][j][i] -= lr * deltas[l][j] * in[i];
        weights[l][j][in.length] -= lr * deltas[l][j]; // bias
      }
    }

    lastDeltas = deltas;
  }

  private double applyActivation(double z, int actType, boolean isOutput) {
    if (isOutput && actType == ACT_SIGMOID) return z; // default output = linear
    return switch (actType) {
      case ACT_RELU -> Math.max(0, z);
      case ACT_TANH -> Math.tanh(z);
      case ACT_LINEAR -> z;
      case ACT_SOFTMAX -> z; // softmax applied as a post-processing step over the whole layer
      default -> 1.0 / (1.0 + Math.exp(-z)); // sigmoid
    };
  }

  private double activationDeriv(double a, int actType) {
    return switch (actType) {
      case ACT_RELU -> a > 0 ? 1.0 : 0.0;
      case ACT_TANH -> 1 - a * a;
      case ACT_LINEAR -> 1.0;
      case ACT_SOFTMAX -> a * (1 - a); // simplified; full Jacobian not needed for cross-entropy
      default -> a * (1 - a); // sigmoid
    };
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  static double[] toDoubleArray(ChuckArray arr) {
    if (arr == null) return new double[0];
    int n = arr.size();
    double[] d = new double[n];
    for (int i = 0; i < n; i++) d[i] = arr.getFloat(i);
    return d;
  }

  static void fillArray(ChuckArray arr, double[] vals) {
    for (int i = 0; i < vals.length && i < arr.size(); i++) arr.setFloat(i, (float) vals[i]);
  }
}
