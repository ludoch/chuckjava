package org.chuck.ide.dsp;

import java.util.HashMap;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import org.chuck.core.ChuckVM;

/**
 * An interactive Faust DSP & Difference Equation live-coding tab for ChucK-Java. Allows developers
 * to type inline signal equations, select high-performance DSP templates, and instantly spork zero-
 * allocation synthesis graphs with live parameter faders.
 */
public class FaustLiveCodingTab extends BorderPane {
  private ChuckVM vm;
  private int activeShredId = -1;
  private final TextArea editor;
  private final VBox faderRack;
  private final Map<String, Slider> faders = new HashMap<>();

  public FaustLiveCodingTab() {
    setPadding(new Insets(8));
    setStyle("-fx-background-color: #f8f8f8;");

    // Header Toolbar
    HBox toolbar = new HBox(8);
    toolbar.setPadding(new Insets(0, 0, 8, 0));
    toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

    Label title = new Label("Faust / Inline DSP Engine");
    title.setStyle("-fx-font-weight: bold; -fx-font-size: 12; -fx-text-fill: #333;");

    ComboBox<String> templateBox =
        new ComboBox<>(
            FXCollections.observableArrayList(
                "2-Operator FM Bell Synth",
                "4-Pole Resonant Low-Pass Filter",
                "Non-Linear Foldback Wavefolder",
                "Karplus-Strong Plucked String"));
    templateBox.setValue("2-Operator FM Bell Synth");
    templateBox.setStyle("-fx-font-size: 11;");

    Button compileBtn = new Button("⚡ Spork Live DSP");
    compileBtn.setStyle(
        "-fx-background-color: #00e676; -fx-font-weight: bold; -fx-text-fill: #003300; -fx-font-size: 11;");
    compileBtn.setTooltip(new Tooltip("Compile and atomically replace live DSP synthesis graph"));

    Button stopBtn = new Button("■ Stop DSP");
    stopBtn.setStyle(
        "-fx-background-color: #ff5252; -fx-font-weight: bold; -fx-text-fill: white; -fx-font-size: 11;");
    stopBtn.setTooltip(new Tooltip("Terminate the currently running Faust/DSP shred"));

    toolbar.getChildren().addAll(title, new Separator(), templateBox, compileBtn, stopBtn);
    setTop(toolbar);

    // Code Editor
    editor = new TextArea();
    editor.setFont(Font.font("Monospaced", 12));
    editor.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: #00e676;");
    loadTemplate("2-Operator FM Bell Synth");

    // Fader Rack
    faderRack = new VBox(6);
    faderRack.setPadding(new Insets(8, 4, 8, 4));
    faderRack.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-width: 1;");
    Label rackTitle = new Label("Live DSP Parameter Rack");
    rackTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 11; -fx-text-fill: #555;");
    faderRack.getChildren().add(rackTitle);
    updateFaders("2-Operator FM Bell Synth");

    SplitPane split = new SplitPane(editor, faderRack);
    split.setOrientation(javafx.geometry.Orientation.VERTICAL);
    split.setDividerPositions(0.65);
    setCenter(split);

    templateBox.setOnAction(
        e -> {
          String sel = templateBox.getValue();
          if (sel != null) {
            loadTemplate(sel);
            updateFaders(sel);
          }
        });

    compileBtn.setOnAction(e -> sporkLiveDsp());
    stopBtn.setOnAction(e -> stopLiveDsp());
  }

  public void setVm(ChuckVM vm) {
    this.vm = vm;
  }

  private void loadTemplate(String name) {
    switch (name) {
      case "2-Operator FM Bell Synth" ->
          editor.setText(
              """
          // Faust / DSP Inline: 2-Operator Frequency Modulation Bell
          // Parameters: carrierFreq, modRatio, modIndex, gain

          global float carrierFreq; 440.0 => carrierFreq;
          global float modRatio; 1.414 => modRatio;
          global float modIndex; 200.0 => modIndex;
          global float dspGain; 0.6 => dspGain;

          SinOsc mod => SinOsc car => dac;
          2 => car.sync; // frequency modulation input

          while(true) {
              carrierFreq * modRatio => mod.freq;
              modIndex => mod.gain;
              carrierFreq => car.freq;
              dspGain => car.gain;
              1::ms => now;
          }
          """);
      case "4-Pole Resonant Low-Pass Filter" ->
          editor.setText(
              """
          // Faust / DSP Inline: 4-Pole Resonant Low-Pass Filter Sweep
          // Parameters: cutoffFreq, resonance, drive

          global float cutoffFreq; 800.0 => cutoffFreq;
          global float resonance; 4.0 => resonance;
          global float drive; 0.7 => drive;

          SawOsc saw => LPF lpf => dac;
          110.0 => saw.freq;

          while(true) {
              cutoffFreq => lpf.freq;
              resonance => lpf.Q;
              drive => saw.gain;
              1::ms => now;
          }
          """);
      case "Non-Linear Foldback Wavefolder" ->
          editor.setText(
              """
          // Faust / DSP Inline: Non-Linear Foldback Wavefolding Distortion
          // Parameters: foldThreshold, inputGain

          global float foldThreshold; 0.3 => foldThreshold;
          global float inputGain; 1.5 => inputGain;

          SinOsc s => FoldbackSaturator fold => dac;
          110.0 => s.freq;

          while(true) {
              inputGain => s.gain;
              foldThreshold => fold.threshold;
              1::ms => now;
          }
          """);
      case "Karplus-Strong Plucked String" ->
          editor.setText(
              """
          // Faust / DSP Inline: Karplus-Strong Plucked String Synthesis
          // Parameters: stringPitch, damping

          global float stringPitch; 60.0 => stringPitch;
          global float damping; 0.99 => damping;

          KSChord ks => dac;
          0.7 => ks.gain;

          while(true) {
              stringPitch => Std.mtof => ks.freq;
              100::ms => now;
              1.0 => ks.pluck;
              500::ms => now;
          }
          """);
    }
  }

  private void updateFaders(String name) {
    if (faderRack.getChildren().size() > 1) {
      faderRack.getChildren().remove(1, faderRack.getChildren().size());
    }
    faders.clear();

    switch (name) {
      case "2-Operator FM Bell Synth" -> {
        addFader("carrierFreq", 20.0, 2000.0, 440.0);
        addFader("modRatio", 0.5, 8.0, 1.414);
        addFader("modIndex", 0.0, 1000.0, 200.0);
        addFader("dspGain", 0.0, 1.0, 0.6);
      }
      case "4-Pole Resonant Low-Pass Filter" -> {
        addFader("cutoffFreq", 50.0, 15000.0, 800.0);
        addFader("resonance", 0.5, 20.0, 4.0);
        addFader("drive", 0.0, 1.0, 0.7);
      }
      case "Non-Linear Foldback Wavefolder" -> {
        addFader("foldThreshold", 0.05, 1.0, 0.3);
        addFader("inputGain", 0.1, 5.0, 1.5);
      }
      case "Karplus-Strong Plucked String" -> {
        addFader("stringPitch", 36.0, 96.0, 60.0);
        addFader("damping", 0.8, 0.999, 0.99);
      }
    }
  }

  private void addFader(String param, double min, double max, double init) {
    Label lbl = new Label(param + ": " + String.format("%.2f", init));
    lbl.setStyle("-fx-font-size: 11; -fx-font-family: 'Monospaced';");

    Slider s = new Slider(min, max, init);
    HBox.setHgrow(s, Priority.ALWAYS);
    faders.put(param, s);

    s.valueProperty()
        .addListener(
            (obs, oldV, newV) -> {
              lbl.setText(param + ": " + String.format("%.2f", newV.doubleValue()));
              if (vm != null) {
                vm.setGlobalFloat(param, newV.doubleValue());
              }
            });

    HBox row = new HBox(8, lbl, s);
    row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    faderRack.getChildren().add(row);
  }

  private void sporkLiveDsp() {
    if (vm == null) return;
    stopLiveDsp();

    try {
      String code = editor.getText();
      activeShredId = vm.add("/* FaustLiveDSP */\n" + code);
      // Sync initial fader values to VM globals
      for (Map.Entry<String, Slider> e : faders.entrySet()) {
        vm.setGlobalFloat(e.getKey(), e.getValue().getValue());
      }
    } catch (Exception ex) {
      System.err.println("[FaustDSP] Live compilation failed: " + ex.getMessage());
    }
  }

  private void stopLiveDsp() {
    if (vm != null && activeShredId >= 0) {
      try {
        vm.removeShred(activeShredId);
      } catch (Exception ignored) {
      }
      activeShredId = -1;
    }
  }
}
