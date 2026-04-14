package org.chuck.core.ai;

import java.util.ArrayList;
import java.util.List;
import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

/**
 * Wekinator — an interactive machine learning system based on Rebecca Fiebrink's Wekinator
 * framework. Wraps the MLP (and optionally KNN/SVM) AI classes to provide a unified train/predict
 * API with configurable model and task types.
 *
 * <p>Model types (AI.* constants): MLP=0, KNN=1, SVM=2. Task types: Regression=0, Classification=1.
 *
 * <p>Corresponds to ChucK's {@code Wekinator} class from {@code ulib_ai.cpp}.
 */
public class Wekinator extends ChuckObject {

  // ── AI constants (mirrored from emitter dispatch) ─────────────────────────
  public static final int MODEL_MLP = 0;
  public static final int MODEL_KNN = 1;
  public static final int MODEL_SVM = 2;
  public static final int TASK_REGRESSION = 0;
  public static final int TASK_CLASSIFICATION = 1;

  // ── Instance state ────────────────────────────────────────────────────────

  private int modelType = MODEL_MLP;
  private int taskType = TASK_REGRESSION;

  // Training data accumulator
  private final List<double[]> trainX = new ArrayList<>();
  private final List<double[]> trainY = new ArrayList<>();

  // Staging buffers for wek.input() / wek.output() / wek.add()
  private double[] stagedInput = new double[0];
  private double[] stagedOutput = new double[0];

  // Trained model (backed by MLP; KNN/SVM degrade to MLP for now)
  private final MLP mlp = new MLP();
  private final KNN knn = new KNN();
  private boolean trained = false;

  // MLP hyper-parameters
  private int hiddenLayers = 1;
  private int nodesPerHiddenLayer = 0; // 0 = same as input
  private double learningRate = 0.01;
  private int epochs = 100;

  public Wekinator() {
    super(ChuckType.OBJECT);
  }

  // ── ChucK API ─────────────────────────────────────────────────────────────

  /** Clear all training observations and reset the trained model. */
  public long clear() {
    trainX.clear();
    trainY.clear();
    trained = false;
    return 0L;
  }

  /** Stage the current input vector. */
  public long input(ChuckArray arr) {
    stagedInput = toDoubleArray(arr);
    return 0L;
  }

  /** Stage the current output vector. */
  public long output(ChuckArray arr) {
    stagedOutput = toDoubleArray(arr);
    return 0L;
  }

  /** Add the currently staged (input, output) pair to the training set. */
  public long add() {
    if (stagedInput.length > 0 && stagedOutput.length > 0) {
      trainX.add(stagedInput.clone());
      trainY.add(stagedOutput.clone());
    }
    return (long) trainX.size();
  }

  /** Train the model on all accumulated observations. */
  public long train() {
    if (trainX.isEmpty()) return 0L;
    int inDim = trainX.get(0).length;
    int outDim = trainY.get(0).length;
    int hidden = (nodesPerHiddenLayer == 0) ? inDim : nodesPerHiddenLayer;

    if (modelType == MODEL_KNN) {
      // Use KNN for both regression and classification
      ChuckArray xArr = buildJaggedArray(trainX);
      ChuckArray yArr = buildJaggedArray(trainY);
      knn.train(xArr, yArr);
    } else {
      // MLP for all other model types
      mlp.input(inDim);
      mlp.hidden(hidden);
      mlp.output(outDim);
      mlp.lr(learningRate);
      mlp.epochs(epochs);
      ChuckArray xArr = buildJaggedArray(trainX);
      ChuckArray yArr = buildJaggedArray(trainY);
      mlp.train(xArr, yArr);
    }
    trained = true;
    return (long) trainX.size();
  }

  /** Predict output from input, filling {@code outputs}. Returns 1 on success. */
  public long predict(ChuckArray inputs, ChuckArray outputs) {
    if (!trained) return 0L;
    if (modelType == MODEL_KNN) {
      return knn.predict(inputs, outputs);
    } else {
      return mlp.predict(inputs, outputs);
    }
  }

  // ── Model / task type ─────────────────────────────────────────────────────

  public long modelType(long t) {
    modelType = (int) t;
    return modelType;
  }

  public long getModelType() {
    return modelType;
  }

  public long taskType(long t) {
    taskType = (int) t;
    return taskType;
  }

  public long getTaskType() {
    return taskType;
  }

  public String modelTypeName() {
    return switch (modelType) {
      case MODEL_MLP -> "MLP";
      case MODEL_KNN -> "KNN";
      case MODEL_SVM -> "SVM";
      default -> "MLP";
    };
  }

  public String taskTypeName() {
    return switch (taskType) {
      case TASK_REGRESSION -> "Regression";
      case TASK_CLASSIFICATION -> "Classification";
      default -> "Regression";
    };
  }

  // ── Property API ──────────────────────────────────────────────────────────

  /** {@code wek.getPropertyInt(AI.MLP, "hiddenLayers")} */
  public long getPropertyInt(long model, String prop) {
    return switch (prop) {
      case "hiddenLayers" -> hiddenLayers;
      case "nodesPerHiddenLayer" -> nodesPerHiddenLayer;
      case "epochs" -> epochs;
      default -> 0L;
    };
  }

  /** {@code wek.getPropertyFloat(AI.MLP, "learningRate")} */
  public double getPropertyFloat(long model, String prop) {
    return switch (prop) {
      case "learningRate" -> learningRate;
      default -> 0.0;
    };
  }

  /** {@code wek.setProperty(AI.MLP, "hiddenLayers", 2)} */
  public long setPropertyInt(long model, String prop, long value) {
    switch (prop) {
      case "hiddenLayers" -> hiddenLayers = (int) value;
      case "nodesPerHiddenLayer" -> nodesPerHiddenLayer = (int) value;
      case "epochs" -> epochs = (int) value;
    }
    return 0L;
  }

  /** {@code wek.setProperty(AI.MLP, "learningRate", 0.001)} */
  public long setPropertyFloat(long model, String prop, double value) {
    if (prop.equals("learningRate")) learningRate = value;
    return 0L;
  }

  /** Unified {@code setProperty} — dispatches to int or float variant by value type. */
  public long setProperty(long model, String prop, double value) {
    if (prop.equals("learningRate")) {
      learningRate = value;
    } else {
      setPropertyInt(model, prop, (long) value);
    }
    return 0L;
  }

  /** Export training observations as a simple CSV-like ARFF stub. */
  public long exportObs(String path) {
    try (java.io.PrintWriter pw = new java.io.PrintWriter(path)) {
      pw.println("@relation wekinator");
      int inDim = trainX.isEmpty() ? 0 : trainX.get(0).length;
      int outDim = trainY.isEmpty() ? 0 : trainY.get(0).length;
      for (int i = 0; i < inDim; i++) pw.println("@attribute input" + i + " NUMERIC");
      for (int i = 0; i < outDim; i++) pw.println("@attribute output" + i + " NUMERIC");
      pw.println("@data");
      for (int r = 0; r < trainX.size(); r++) {
        StringBuilder sb = new StringBuilder();
        for (double v : trainX.get(r)) sb.append(v).append(',');
        for (int i = 0; i < trainY.get(r).length; i++) {
          sb.append(trainY.get(r)[i]);
          if (i < trainY.get(r).length - 1) sb.append(',');
        }
        pw.println(sb);
      }
    } catch (Exception e) {
      return 0L;
    }
    return 1L;
  }

  public long numObs() {
    return trainX.size();
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private static double[] toDoubleArray(ChuckArray arr) {
    if (arr == null) return new double[0];
    int n = arr.size();
    double[] d = new double[n];
    for (int i = 0; i < n; i++) d[i] = arr.getFloat(i);
    return d;
  }

  private static ChuckArray buildJaggedArray(List<double[]> rows) {
    ChuckArray outer = new ChuckArray(ChuckType.ARRAY, rows.size());
    for (int i = 0; i < rows.size(); i++) {
      double[] row = rows.get(i);
      ChuckArray inner = new ChuckArray(ChuckType.ARRAY, row.length);
      for (int j = 0; j < row.length; j++) inner.setFloat(j, row[j]);
      outer.setObject(i, inner);
    }
    return outer;
  }
}
