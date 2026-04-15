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
  private final List<Integer> obsRounds = new ArrayList<>();

  // Staging buffers for wek.input() / wek.output() / wek.add()
  private double[] stagedInput = new double[0];
  private double[] stagedOutput = new double[0];

  // Dimension hints (0 = auto-detect)
  private int inputDimHint = 0;
  private int outputDimHint = 0;

  // Round counter for wek.nextRound() / wek.deleteLastRound()
  private int roundNumber = 0;

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
    roundNumber = 0;
    trained = false;
    return 0L;
  }

  /** Clear all observations (alias). */
  public long clearAllObs() {
    return clear();
  }

  /** Clear all observations on channel (stub — single-channel impl; ignored). */
  public long clearAllObs(long channel) {
    return clear();
  }

  /** Clear observations by ID range [lo, hi]. */
  public long clearObs(long lo, long hi) {
    int l = (int) lo, h = (int) hi;
    for (int i = Math.min(h, trainX.size() - 1); i >= l && i >= 0; i--) {
      trainX.remove(i);
      trainY.remove(i);
    }
    return 0L;
  }

  /** Clear observations by ID range [lo, hi] on channel (stub — uses global list). */
  public long clearObs(long channel, long lo, long hi) {
    return clearObs(lo, hi);
  }

  /** Set number of input dimensions (clears data if changed). */
  public long inputDims(long n) {
    if (n != inputDimHint) {
      inputDimHint = (int) n;
      clear();
    }
    return n;
  }

  /** Get input dimension hint (0 = derived from first observation). */
  public long inputDims() {
    return inputDimHint > 0 ? inputDimHint : (trainX.isEmpty() ? 0 : trainX.get(0).length);
  }

  /** Set number of output dimensions (clears data if changed). */
  public long outputDims(long n) {
    if (n != outputDimHint) {
      outputDimHint = (int) n;
      clear();
    }
    return n;
  }

  /** Get output dimension hint (0 = derived from first observation). */
  public long outputDims() {
    return outputDimHint > 0 ? outputDimHint : (trainY.isEmpty() ? 0 : trainY.get(0).length);
  }

  /** Increment the round counter. */
  public long nextRound() {
    roundNumber++;
    return roundNumber;
  }

  /** Delete observations added in the last round. */
  public long deleteLastRound() {
    int target = roundNumber - 1;
    for (int i = obsRounds.size() - 1; i >= 0; i--) {
      if (obsRounds.get(i) == target) {
        trainX.remove(i);
        trainY.remove(i);
        obsRounds.remove(i);
      }
    }
    if (roundNumber > 0) roundNumber--;
    return 0L;
  }

  /** Randomize the current staged outputs between 0 and 1. */
  public long randomizeOutputs() {
    int dim = outputDimHint > 0 ? outputDimHint : stagedOutput.length;
    stagedOutput = new double[dim];
    for (int i = 0; i < dim; i++) stagedOutput[i] = Math.random();
    return 0L;
  }

  /** Add an explicit (input, output) pair. */
  public long add(ChuckArray inputArr, ChuckArray outputArr) {
    double[] x = toDoubleArray(inputArr);
    double[] y = toDoubleArray(outputArr);
    if (x.length > 0 && y.length > 0) {
      trainX.add(x);
      trainY.add(y);
      obsRounds.add(roundNumber);
    }
    return (long) trainX.size();
  }

  /** Add an explicit (input, output) pair for a specific output channel (stub). */
  public long add(long channel, ChuckArray inputArr, ChuckArray outputArr) {
    return add(inputArr, outputArr);
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
      obsRounds.add(roundNumber);
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
    } else if (prop.equals("limit")) {
      // boolean/int — ignore for now
    } else {
      setPropertyInt(model, prop, (long) value);
    }
    return 0L;
  }

  // ── Per-output channel API (stubs — single-channel impl) ─────────────────

  /** {@code wek.setOutputProperty(ch, prop, value)} — stub, delegates to global. */
  public long setOutputProperty(long ch, String prop, double value) {
    return setProperty(modelType, prop, value);
  }

  /** {@code wek.setOutputProperty(ch, model, prop, value)} — stub, delegates to global. */
  public long setOutputProperty(long ch, long model, String prop, double value) {
    return setProperty(model, prop, value);
  }

  /** {@code wek.setOutputProperty(ch, prop, arr)} — stub. */
  public long setOutputProperty(long ch, String prop, ChuckArray value) {
    return 0L;
  }

  /** {@code wek.getOutputPropertyInt(ch, prop)} — stub, reads global. */
  public long getOutputPropertyInt(long ch, String prop) {
    return getPropertyInt(modelType, prop);
  }

  /** {@code wek.getOutputPropertyInt(ch, model, prop)} — stub. */
  public long getOutputPropertyInt(long ch, long model, String prop) {
    return getPropertyInt(model, prop);
  }

  /** {@code wek.getOutputPropertyFloat(ch, model, prop)} — stub. */
  public double getOutputPropertyFloat(long ch, long model, String prop) {
    return getPropertyFloat(model, prop);
  }

  /** {@code wek.getOutputProperty(ch, prop, result)} — stub. */
  public long getOutputProperty(long ch, String prop, ChuckArray result) {
    return 0L;
  }

  /** {@code wek.setAllRecordStatus(bool)} — stub. */
  public long setAllRecordStatus(long v) {
    return 0L;
  }

  /** {@code wek.setOutputRecordStatus(ch, bool)} — stub. */
  public long setOutputRecordStatus(long ch, long v) {
    return 0L;
  }

  /** {@code wek.getOutputRecordStatus(ch)} — stub returns 1. */
  public long getOutputRecordStatus(long ch) {
    return 1L;
  }

  /** {@code wek.setAllRunStatus(bool)} — stub. */
  public long setAllRunStatus(long v) {
    return 0L;
  }

  /** {@code wek.setOutputRunStatus(ch, bool)} — stub. */
  public long setOutputRunStatus(long ch, long v) {
    return 0L;
  }

  /** {@code wek.getOutputRunStatus(ch)} — stub returns 1. */
  public long getOutputRunStatus(long ch) {
    return 1L;
  }

  // ── Persistence ───────────────────────────────────────────────────────────

  /** Export training observations as ARFF stub. */
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

  /** Export observations for a specific channel (stub — same as global). */
  public long exportObs(long ch, String path) {
    return exportObs(path);
  }

  /** Import observations from ARFF file. */
  public long importObs(String path) {
    try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(path))) {
      String line;
      boolean inData = false;
      trainX.clear();
      trainY.clear();
      obsRounds.clear();
      int inDim = 0, outDim = 0;
      while ((line = br.readLine()) != null) {
        line = line.trim();
        if (line.startsWith("@attribute input")) inDim++;
        else if (line.startsWith("@attribute output")) outDim++;
        else if (line.equalsIgnoreCase("@data")) inData = true;
        else if (inData && !line.isEmpty()) {
          String[] parts = line.split(",");
          double[] x = new double[inDim], y = new double[outDim];
          for (int i = 0; i < inDim && i < parts.length; i++) x[i] = Double.parseDouble(parts[i]);
          for (int i = 0; i < outDim && inDim + i < parts.length; i++)
            y[i] = Double.parseDouble(parts[inDim + i]);
          trainX.add(x);
          trainY.add(y);
          obsRounds.add(0);
        }
      }
    } catch (Exception e) {
      return 0L;
    }
    return 1L;
  }

  /** Save full Wekinator state to file. */
  public long save(String path) {
    return exportObs(path); // minimal: save observations only
  }

  /** Load Wekinator state from file. */
  public long load(String path) {
    return importObs(path);
  }

  /** Retrieve all observations into a 2D ChuckArray. */
  public long getObs(ChuckArray result) {
    for (int i = 0; i < trainX.size() && i < result.size(); i++) {
      Object row = result.getObject(i);
      if (row instanceof ChuckArray ra) {
        double[] x = trainX.get(i), y = trainY.get(i);
        int k = 0;
        for (int j = 0; j < x.length && k < ra.size(); j++) ra.setFloat(k++, x[j]);
        for (int j = 0; j < y.length && k < ra.size(); j++) ra.setFloat(k++, y[j]);
      }
    }
    return trainX.size();
  }

  /** Retrieve observations for a specific channel (stub — same as global). */
  public long getObs(long ch, ChuckArray result) {
    return getObs(result);
  }

  public long numObs() {
    return trainX.size();
  }

  /** Num observations for a specific output channel (stub — returns global count). */
  public long numObs(long ch) {
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
