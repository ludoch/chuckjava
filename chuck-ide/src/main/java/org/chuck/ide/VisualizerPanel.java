package org.chuck.ide;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.chuck.audio.ChuckAudio;
import org.chuck.audio.analysis.FFT;
import org.chuck.audio.util.Scope;
import org.chuck.core.ChuckVM;

/** Encapsulates the visualizers (Spectrum, Oscilloscope, Waterfall, Phase). */
public class VisualizerPanel extends VBox {
  private final Canvas visualizerCanvas = new Canvas();
  private final Canvas scopeCanvas = new Canvas();
  private final Canvas waterfallCanvas = new Canvas();
  private final Canvas phaseScopeCanvas = new Canvas();

  private final FFT analyzer;
  private final Scope scope;
  private final ChuckVM vm;
  private final ChuckAudio audio;

  private AnimationTimer visTimer;

  public VisualizerPanel(ChuckVM vm, ChuckAudio audio, FFT analyzer, Scope scope) {
    super(2);
    this.vm = vm;
    this.audio = audio;
    this.analyzer = analyzer;
    this.scope = scope;

    setupUI();
  }

  private void setupUI() {
    setStyle("-fx-background-color: #222; -fx-padding: 5;");

    Label specLabel = new Label("Spectrum");
    StackPane specStack = createStack(visualizerCanvas);

    Label scopeLabel = new Label("Oscilloscope");
    StackPane scopeStack = createStack(scopeCanvas);

    Label waterLabel = new Label("Waterfall");
    StackPane waterStack = createStack(waterfallCanvas);

    Label phaseLabel = new Label("Stereo Phase");
    StackPane phaseStack = createStack(phaseScopeCanvas);

    for (Label l : new Label[] {specLabel, scopeLabel, waterLabel, phaseLabel}) {
      l.setTextFill(Color.LIGHTGRAY);
      l.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 11;");
    }

    getChildren()
        .addAll(
            specLabel,
            specStack,
            scopeLabel,
            scopeStack,
            waterLabel,
            waterStack,
            phaseLabel,
            phaseStack);

    VBox.setVgrow(specStack, Priority.ALWAYS);
    VBox.setVgrow(scopeStack, Priority.ALWAYS);
    VBox.setVgrow(waterStack, Priority.ALWAYS);
    VBox.setVgrow(phaseStack, Priority.ALWAYS);
  }

  private StackPane createStack(Canvas c) {
    StackPane sp = new StackPane(c);
    sp.setStyle("-fx-background-color: black;");
    sp.setPrefHeight(80);
    c.widthProperty().bind(sp.widthProperty());
    c.heightProperty().bind(sp.heightProperty());
    return sp;
  }

  public void start() {
    visTimer =
        new AnimationTimer() {
          private long lastSlowTick = 0;
          private boolean wasIdle = false;
          private long silenceStartTime = 0;

          @Override
          public void handle(long now) {
            boolean currentSilence =
                audio != null && (vm.getActiveShredCount() == 0) && audio.getPeakOut(0) < 0.0001;

            if (currentSilence) {
              if (silenceStartTime == 0) silenceStartTime = now;
            } else {
              silenceStartTime = 0;
            }

            boolean shouldBeIdle =
                (silenceStartTime != 0) && (now - silenceStartTime > 1_000_000_000L);

            if (!shouldBeIdle) {
              renderSpectrum();
              renderScope();
              renderWaterfall();
              renderPhaseScope();
              wasIdle = false;
            } else if (!wasIdle) {
              clearVisualizers();
              wasIdle = true;
            }
          }
        };
    visTimer.start();
  }

  public void stop() {
    if (visTimer != null) visTimer.stop();
  }

  private float[] smoothedMags;

  private void renderSpectrum() {
    GraphicsContext gc = visualizerCanvas.getGraphicsContext2D();
    double w = visualizerCanvas.getWidth(), h = visualizerCanvas.getHeight();
    if (w <= 0 || h <= 0 || analyzer == null) return;

    // Background & dB Gridlines
    gc.setFill(Color.rgb(18, 18, 18));
    gc.fillRect(0, 0, w, h);

    gc.setStroke(Color.rgb(50, 50, 50));
    gc.setLineWidth(0.5);
    gc.setFont(javafx.scene.text.Font.font("Monospaced", 9));
    gc.setFill(Color.rgb(120, 120, 120));
    for (int dbGrid : new int[] {-60, -40, -20}) {
      double y = h - ((dbGrid + 80.0) / 80.0 * h);
      gc.strokeLine(0, y, w, y);
      gc.fillText(dbGrid + " dB", 3, y - 2);
    }

    // Logarithmic Frequency Gridlines (20 Hz to 20 kHz)
    double minFreq = 20.0, maxFreq = 20000.0;
    for (double freqGrid : new double[] {100, 1000, 10000}) {
      double x = w * Math.log(freqGrid / minFreq) / Math.log(maxFreq / minFreq);
      gc.strokeLine(x, 0, x, h);
      String label = freqGrid >= 1000 ? (int) (freqGrid / 1000) + "k" : (int) freqGrid + "Hz";
      gc.fillText(label, x + 3, h - 3);
    }

    float[] mags = analyzer.getLatestMags();
    if (mags == null || mags.length == 0) return;

    if (smoothedMags == null || smoothedMags.length != mags.length) {
      smoothedMags = new float[mags.length];
    }
    for (int i = 0; i < mags.length; i++) {
      smoothedMags[i] = 0.82f * smoothedMags[i] + 0.18f * mags[i];
    }

    double srate = vm.getSampleRate();
    double nyquist = srate / 2.0;

    gc.beginPath();
    gc.moveTo(0, h);
    for (int x = 0; x < (int) w; x++) {
      double freq = minFreq * Math.pow(maxFreq / minFreq, x / w);
      int bin = (int) (freq / nyquist * mags.length);
      if (bin < 0) bin = 0;
      if (bin >= mags.length) bin = mags.length - 1;

      double linear = smoothedMags[bin] * (2.0 / mags.length);
      double db = linear > 1e-9 ? 20.0 * Math.log10(linear) : -80.0;
      double val = Math.max(0.0, Math.min(1.0, (db + 80.0) / 80.0));
      double y = h - (val * h);

      gc.lineTo(x, y);
    }
    gc.lineTo(w, h);
    gc.closePath();

    // Translucent gradient fill under spectrum curve
    gc.setFill(Color.rgb(0, 255, 100, 0.15));
    gc.fill();

    // Crisp top outline stroke
    gc.setStroke(Color.LIME);
    gc.setLineWidth(1.5);
    gc.beginPath();
    for (int x = 0; x < (int) w; x++) {
      double freq = minFreq * Math.pow(maxFreq / minFreq, x / w);
      int bin = (int) (freq / nyquist * mags.length);
      if (bin < 0) bin = 0;
      if (bin >= mags.length) bin = mags.length - 1;

      double linear = smoothedMags[bin] * (2.0 / mags.length);
      double db = linear > 1e-9 ? 20.0 * Math.log10(linear) : -80.0;
      double val = Math.max(0.0, Math.min(1.0, (db + 80.0) / 80.0));
      double y = h - (val * h);

      if (x == 0) gc.moveTo(x, y);
      else gc.lineTo(x, y);
    }
    gc.stroke();
  }

  private void renderScope() {
    GraphicsContext gc = scopeCanvas.getGraphicsContext2D();
    double w = scopeCanvas.getWidth(), h = scopeCanvas.getHeight();
    if (w <= 0 || h <= 0) return;

    gc.setFill(Color.rgb(15, 18, 15));
    gc.fillRect(0, 0, w, h);

    // Center 0V baseline
    gc.setStroke(Color.rgb(45, 75, 45));
    gc.setLineWidth(0.8);
    gc.setLineDashes(4, 4);
    gc.strokeLine(0, h / 2.0, w, h / 2.0);
    gc.setLineDashes(null);

    float[] data = vm.getDacChannel(0).getVisBuffer();
    if (data == null || data.length < 4) return;

    // Zero-Crossing Trigger Lock search (positive slope through 0V)
    int triggerIdx = 0;
    for (int i = 0; i < data.length / 2; i++) {
      if (data[i] <= 0.0001f && data[i + 1] > 0.0001f && (data[i + 1] - data[i]) > 0.00005f) {
        triggerIdx = i;
        break;
      }
    }

    int len = data.length - triggerIdx;
    if (len < 2) return;
    double xStep = w / (double) (len - 1);

    // Pass 1: Phosphor glow pass
    gc.setStroke(Color.rgb(0, 255, 100, 0.25));
    gc.setLineWidth(3.5);
    gc.beginPath();
    for (int i = 0; i < len; i++) {
      double y = (h / 2.0) - (data[triggerIdx + i] * (h / 2.0) * 0.9);
      if (i == 0) gc.moveTo(0, y);
      else gc.lineTo(i * xStep, y);
    }
    gc.stroke();

    // Pass 2: High-def trace line
    gc.setStroke(Color.LIME);
    gc.setLineWidth(1.5);
    gc.beginPath();
    for (int i = 0; i < len; i++) {
      double y = (h / 2.0) - (data[triggerIdx + i] * (h / 2.0) * 0.9);
      if (i == 0) gc.moveTo(0, y);
      else gc.lineTo(i * xStep, y);
    }
    gc.stroke();
  }

  private void renderWaterfall() {
    GraphicsContext gc = waterfallCanvas.getGraphicsContext2D();
    double w = waterfallCanvas.getWidth(), h = waterfallCanvas.getHeight();
    if (w <= 0 || h <= 0 || analyzer == null) return;
    float[] mags = analyzer.getLatestMags();
    if (mags == null || mags.length == 0) return;
    gc.drawImage(waterfallCanvas.snapshot(null, null), 0, 1);
    double binW = w / mags.length, norm = 2.0 / mags.length;
    for (int i = 0; i < mags.length; i++) {
      double linear = mags[i] * norm;
      double db = linear > 1e-9 ? 20.0 * Math.log10(linear) : -80.0;
      double val = Math.max(0.0, Math.min(1.0, (db + 80.0) / 80.0));
      gc.setFill(getWaterfallColor(val));
      gc.fillRect(i * binW, 0, binW + 1, 1);
    }
  }

  private Color getWaterfallColor(double intensity) {
    if (intensity < 0.2) return Color.BLACK;
    if (intensity < 0.4) return Color.BLUE;
    if (intensity < 0.6) return Color.RED;
    if (intensity < 0.8) return Color.YELLOW;
    return Color.WHITE;
  }

  private void renderPhaseScope() {
    GraphicsContext gc = phaseScopeCanvas.getGraphicsContext2D();
    double w = phaseScopeCanvas.getWidth(), h = phaseScopeCanvas.getHeight();
    if (w <= 0 || h <= 0) return;
    gc.setFill(Color.rgb(0, 0, 0, 0.2));
    gc.fillRect(0, 0, w, h);
    float[] dataL = vm.getDacChannel(0).getVisBuffer();
    float[] dataR = vm.getDacChannel(1).getVisBuffer();
    if (dataL == null || dataR == null || dataL.length == 0 || dataR.length == 0) return;
    int len = Math.min(dataL.length, dataR.length);
    gc.setStroke(Color.CYAN);
    gc.setLineWidth(1.0);
    gc.beginPath();
    double centerX = w / 2.0, centerY = h / 2.0;
    for (int i = 0; i < len; i++) {
      double x = centerX + (dataL[i] * centerX * 0.9);
      double y = centerY - (dataR[i] * centerY * 0.9);
      if (i == 0) gc.moveTo(x, y);
      else gc.lineTo(x, y);
    }
    gc.stroke();
  }

  private void clearVisualizers() {
    for (Canvas c :
        new Canvas[] {visualizerCanvas, scopeCanvas, waterfallCanvas, phaseScopeCanvas}) {
      GraphicsContext gc = c.getGraphicsContext2D();
      gc.setFill(Color.BLACK);
      gc.fillRect(0, 0, c.getWidth(), c.getHeight());
    }
  }
}
