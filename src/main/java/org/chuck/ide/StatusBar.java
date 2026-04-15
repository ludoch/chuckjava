package org.chuck.ide;

import javafx.geometry.Orientation;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.chuck.audio.ChuckAudio;
import org.chuck.core.ChuckVM;

/** Bottom status bar for the IDE. */
public class StatusBar extends HBox {
  private final Label lineColLabel = new Label("Ln 1, Col 1");
  private final Label cpuLabel = new Label("CPU: 0.0%");
  private final Label sampleRateLabel = new Label("44100 Hz");
  private final Label vmTimeLabel = new Label("Time: 0.000s");
  private final Label fileNameLabel = new Label("Untitled.ck");

  private final ProgressBar vuLeft = new ProgressBar(0);
  private final ProgressBar vuRight = new ProgressBar(0);

  private final ChuckVM vm;
  private final ChuckAudio audio;

  public StatusBar(ChuckVM vm, ChuckAudio audio, int prefSampleRate) {
    super(10);
    this.vm = vm;
    this.audio = audio;
    this.sampleRateLabel.setText(prefSampleRate + " Hz");

    setupUI();
  }

  private void setupUI() {
    setStyle("-fx-background-color: #ddd; -fx-padding: 2;");
    setAlignment(javafx.geometry.Pos.CENTER_LEFT);

    lineColLabel.setPrefWidth(100);
    cpuLabel.setPrefWidth(85);
    sampleRateLabel.setPrefWidth(80);
    fileNameLabel.setPrefWidth(180);
    fileNameLabel.setEllipsisString("...");

    vuLeft.setPrefWidth(60);
    vuLeft.setPrefHeight(12);
    vuRight.setPrefWidth(60);
    vuRight.setPrefHeight(12);

    Region spacer = new Region();
    HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

    getChildren()
        .addAll(
            fileNameLabel,
            new Separator(Orientation.VERTICAL),
            vmTimeLabel,
            new Separator(Orientation.VERTICAL),
            lineColLabel,
            new Separator(Orientation.VERTICAL),
            sampleRateLabel,
            new Separator(Orientation.VERTICAL),
            cpuLabel,
            spacer,
            new Label("L "),
            vuLeft,
            new Label(" R "),
            vuRight);
  }

  public void setCaretPosition(int line, int col) {
    lineColLabel.setText("Ln " + line + ", Col " + col);
  }

  public void setFileName(String name) {
    fileNameLabel.setText(name);
  }

  public void updateVMText() {
    long nowSample = vm.getCurrentTime();
    double seconds = nowSample / (double) vm.getSampleRate();
    vmTimeLabel.setText(String.format("Time: %.3fs", seconds));
  }

  public void updateCpuLoad() {
    double load = audio.getCpuLoad() * 100.0;
    cpuLabel.setText(String.format("CPU: %.1f%%", load));
  }

  public void updateVUMeters() {
    double p0 = Math.min(1.0, audio.getPeakOut(0));
    double p1 = Math.min(1.0, audio.getPeakOut(1));
    vuLeft.setProgress(p0);
    vuRight.setProgress(p1);

    String leftStyle =
        p0 > 0.707 ? "-fx-accent: red;" : p0 > 0.5 ? "-fx-accent: gold;" : "-fx-accent: limegreen;";
    String rightStyle =
        p1 > 0.707 ? "-fx-accent: red;" : p1 > 0.5 ? "-fx-accent: gold;" : "-fx-accent: limegreen;";

    if (!leftStyle.equals(vuLeft.getStyle())) vuLeft.setStyle(leftStyle);
    if (!rightStyle.equals(vuRight.getStyle())) vuRight.setStyle(rightStyle);
  }
}
