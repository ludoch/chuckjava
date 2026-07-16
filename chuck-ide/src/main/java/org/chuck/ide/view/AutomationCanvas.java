package org.chuck.ide.view;

import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.chuck.ide.model.AutomationTrack;

/** An interactive canvas for viewing, editing, and generating automation curve breakpoints. */
public class AutomationCanvas extends VBox {
  private final AutomationTrack track;
  private final Canvas canvas;
  private double minVal = 0.0;
  private double maxVal = 1.0;
  private Runnable onCurveModified;

  public AutomationCanvas(AutomationTrack track, double minVal, double maxVal) {
    this.track = track;
    this.minVal = minVal;
    this.maxVal = maxVal;

    setSpacing(4);
    setPadding(new Insets(4));
    setStyle(
        "-fx-background-color: #222; -fx-border-color: #444; -fx-border-radius: 4; -fx-background-radius: 4;");

    canvas = new Canvas(280, 80);
    canvas.setOnMousePressed(this::handleCanvasMouse);
    canvas.setOnMouseDragged(this::handleCanvasMouse);

    ComboBox<String> presetCombo = new ComboBox<>();
    presetCombo
        .getItems()
        .addAll(
            "Presets...",
            "Sine LFO (1x)",
            "Sine LFO (2x)",
            "Triangle LFO",
            "Ramp Up",
            "Ramp Down",
            "Random S&H");
    presetCombo.getSelectionModel().selectFirst();
    presetCombo.setStyle("-fx-font-size: 10; -fx-pref-height: 22;");
    presetCombo.setOnAction(
        e -> {
          String sel = presetCombo.getValue();
          if (sel == null || "Presets...".equals(sel)) return;
          switch (sel) {
            case "Sine LFO (1x)" -> track.generateSineLFO(this.minVal, this.maxVal, 1.0);
            case "Sine LFO (2x)" -> track.generateSineLFO(this.minVal, this.maxVal, 2.0);
            case "Triangle LFO" -> track.generateTriangleLFO(this.minVal, this.maxVal, 1.0);
            case "Ramp Up" -> track.generateRampUp(this.minVal, this.maxVal);
            case "Ramp Down" -> track.generateRampDown(this.minVal, this.maxVal);
            case "Random S&H" -> track.generateRandomSH(this.minVal, this.maxVal, 8);
          }
          draw();
          if (onCurveModified != null) onCurveModified.run();
        });

    Button clearBtn = new Button("Clear Curve");
    clearBtn.setStyle(
        "-fx-font-size: 10; -fx-pref-height: 22; -fx-base: #444; -fx-text-fill: white;");
    clearBtn.setTooltip(new Tooltip("Clear all recorded breakpoints"));
    clearBtn.setOnAction(
        e -> {
          track.clear();
          draw();
          if (onCurveModified != null) onCurveModified.run();
        });

    HBox toolBar = new HBox(5, presetCombo, clearBtn);
    getChildren().addAll(canvas, toolBar);
    draw();
  }

  public void setRange(double minVal, double maxVal) {
    this.minVal = minVal;
    this.maxVal = maxVal;
    draw();
  }

  public void setOnCurveModified(Runnable callback) {
    this.onCurveModified = callback;
  }

  private void handleCanvasMouse(MouseEvent e) {
    double w = canvas.getWidth();
    double h = canvas.getHeight();
    if (w <= 0 || h <= 0) return;

    double normX = Math.max(0, Math.min(1.0, e.getX() / w));
    double normY = 1.0 - Math.max(0, Math.min(1.0, e.getY() / h));

    double timeSamples = normX * track.getLoopDurationSamples();
    double value = minVal + normY * (maxVal - minVal);

    track.addPoint(timeSamples, value);
    draw();
    if (onCurveModified != null) onCurveModified.run();
  }

  public void draw() {
    GraphicsContext g = canvas.getGraphicsContext2D();
    double w = canvas.getWidth();
    double h = canvas.getHeight();

    g.setFill(Color.web("#1a1a1a"));
    g.fillRect(0, 0, w, h);

    // Draw grid lines
    g.setStroke(Color.web("#2a2a2a"));
    g.setLineWidth(1);
    for (int i = 1; i < 4; i++) {
      double y = (i / 4.0) * h;
      g.strokeLine(0, y, w, y);
      double x = (i / 4.0) * w;
      g.strokeLine(x, 0, x, h);
    }

    List<AutomationTrack.Breakpoint> points = track.getPoints();
    if (points.isEmpty()) {
      g.setFill(Color.web("#666"));
      g.fillText("No breakpoints recorded. Click/drag or select a preset.", 10, h / 2.0 + 4);
      return;
    }

    double loopDur = track.getLoopDurationSamples();
    if (loopDur <= 0) loopDur = 44100.0 * 2.0;

    g.setStroke(Color.web("#00e676"));
    g.setLineWidth(2);

    // Draw curve interpolation
    double lastX = -1;
    double lastY = -1;
    int steps = (int) w;
    for (int px = 0; px <= steps; px++) {
      double t = (px / w) * loopDur;
      double val = track.evaluate(t, minVal);
      double normVal = (val - minVal) / Math.max(0.0001, maxVal - minVal);
      double py = h - Math.max(0, Math.min(1.0, normVal)) * h;

      if (lastX >= 0) {
        g.strokeLine(lastX, lastY, px, py);
      }
      lastX = px;
      lastY = py;
    }

    // Draw breakpoint nodes
    g.setFill(Color.web("#ffffff"));
    for (AutomationTrack.Breakpoint p : points) {
      double normX = p.timeSamples() / loopDur;
      double normY = (p.value() - minVal) / Math.max(0.0001, maxVal - minVal);
      double px = normX * w;
      double py = h - normY * h;
      g.fillOval(px - 3, py - 3, 6, 6);
    }
  }
}
