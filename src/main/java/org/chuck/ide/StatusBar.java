package org.chuck.ide;

import javafx.geometry.Orientation;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import org.chuck.audio.ChuckAudio;
import org.chuck.core.ChuckVM;

/** Bottom status bar for the IDE. */
public class StatusBar extends HBox {
  private final Label lineColLabel = new Label("Ln 1, Col 1");
  private final Label cpuLabel = new Label("CPU: 0.0%");
  private final Label sampleRateLabel = new Label("44100 Hz");
  private final Label vmTimeLabel = new Label("Time: 0.000s");
  private final Label fileNameLabel = new Label("Untitled.ck");

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

    lineColLabel.setPrefWidth(100);
    cpuLabel.setPrefWidth(85);
    sampleRateLabel.setPrefWidth(80);
    fileNameLabel.setPrefWidth(180);
    fileNameLabel.setEllipsisString("...");

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
            cpuLabel);
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
}
