package org.chuck.ide;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.chuck.core.ChuckVM;
import org.chuck.ide.model.AutomationTrack;
import org.chuck.ide.view.AutomationCanvas;
import org.chuck.midi.ChuckMidiNative;

/**
 * A control surface for ChucK global variables with MIDI CC auto-mapping (MIDI Learn), real-time
 * parameter breakpoint automation recording, LFO generation, and custom scaling.
 */
public class ControlSurface extends VBox {
  private ChuckVM vm;
  private final Map<String, ControlRow> rows = new HashMap<>();
  private final Timeline timeline;
  private final VBox rowContainer;
  private final Preferences prefs = Preferences.userNodeForPackage(ControlSurface.class);

  public ControlSurface() {
    setSpacing(8);
    setPadding(new Insets(8));
    setStyle("-fx-background-color: #f8f8f8; -fx-border-color: #ccc; -fx-border-width: 0 1 0 0;");

    HBox titleBox = new HBox(10);
    Label title = new Label("Control Surface & Automation");
    title.setStyle("-fx-font-weight: bold; -fx-text-fill: #333; -fx-font-size: 13;");
    titleBox.getChildren().add(title);
    getChildren().add(titleBox);

    rowContainer = new VBox(6);
    getChildren().add(rowContainer);

    timeline = new Timeline(new KeyFrame(Duration.millis(80), e -> update()));
    timeline.setCycleCount(Timeline.INDEFINITE);

    // Global MIDI Monitor for Learn feature & Automation Recording
    ChuckMidiNative.addMonitor(
        (device, msg) -> {
          int status = msg.data1 & 0xF0;
          if (status == 0xB0) { // Control Change
            int channel = msg.data1 & 0x0F;
            int cc = msg.data2;
            int val = msg.data3;
            Platform.runLater(() -> handleMidiCc(channel, cc, val));
          }
        });
  }

  private void handleMidiCc(int channel, int cc, int val) {
    for (ControlRow row : rows.values()) {
      if (row.isLearning) {
        row.mappedChannel = channel;
        row.mappedCc = cc;
        row.isLearning = false;
        row.learnBtn.setStyle(
            "-fx-background-color: lightgreen; -fx-font-size: 10; -fx-padding: 2 4;");
        row.learnBtn.setTooltip(new Tooltip("Mapped to CC " + cc + " (Ch " + (channel + 1) + ")"));
        row.midiLabel.setText("CC " + cc);

        // Persist mapping
        prefs.putInt("midi.learn.chan." + row.key, channel);
        prefs.putInt("midi.learn.cc." + row.key, cc);
      }
      if (row.mappedChannel == channel && row.mappedCc == cc) {
        row.onMidiCc(val);
      }
    }
  }

  public void setVm(ChuckVM vm) {
    this.vm = vm;
    if (vm != null) {
      timeline.play();
    } else {
      timeline.stop();
      rowContainer.getChildren().clear();
      rows.clear();
    }
  }

  private void update() {
    if (vm == null) return;

    Set<String> intKeys = vm.getGlobalIntKeys();
    Set<String> floatKeys = vm.getGlobalFloatKeys();

    Set<String> allKeys = new HashSet<>(intKeys);
    allKeys.addAll(floatKeys);

    // Remove rows for variables that no longer exist
    Set<String> toRemove = new HashSet<>(rows.keySet());
    toRemove.removeAll(allKeys);
    for (String key : toRemove) {
      ControlRow row = rows.remove(key);
      rowContainer.getChildren().remove(row.getNode());
    }

    // Add or update rows
    for (String key : intKeys) {
      if (!rows.containsKey(key)) {
        ControlRow row = new ControlRow(key, true, vm.getGlobalInt(key));
        rows.put(key, row);
        rowContainer.getChildren().add(row.getNode());
      }
      rows.get(key).update(vm.getGlobalInt(key));
    }

    for (String key : floatKeys) {
      if (!rows.containsKey(key)) {
        ControlRow row = new ControlRow(key, false, vm.getGlobalFloat(key));
        rows.put(key, row);
        rowContainer.getChildren().add(row.getNode());
      }
      rows.get(key).update(vm.getGlobalFloat(key));
    }
  }

  private class ControlRow {
    private final String key;
    private final boolean isInt;
    private final VBox node;
    private final Slider slider;
    private final Label valueLabel;
    private boolean isDragging = false;

    // MIDI Learn State
    boolean isLearning = false;
    int mappedChannel = -1;
    int mappedCc = -1;
    final Button learnBtn;
    final Label midiLabel;

    // Automation State
    final AutomationTrack automationTrack = new AutomationTrack();
    final AutomationCanvas automationCanvas;
    final ToggleButton recBtn;
    final ToggleButton playBtn;
    final ToggleButton curveBtn;
    final VBox expandBox;

    public ControlRow(String key, boolean isInt, double initialValue) {
      this.key = key;
      this.isInt = isInt;

      // Load persisted mapping
      this.mappedChannel = prefs.getInt("midi.learn.chan." + key, -1);
      this.mappedCc = prefs.getInt("midi.learn.cc." + key, -1);

      Label nameLabel = new Label(key);
      nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");

      midiLabel = new Label("");
      midiLabel.setStyle("-fx-font-size: 9; -fx-text-fill: #666;");

      learnBtn = new Button("L");
      learnBtn.setStyle("-fx-font-size: 10; -fx-padding: 2 4;");

      if (mappedChannel >= 0) {
        learnBtn.setStyle("-fx-background-color: lightgreen; -fx-font-size: 10; -fx-padding: 2 4;");
        learnBtn.setTooltip(
            new Tooltip("Mapped to CC " + mappedCc + " (Ch " + (mappedChannel + 1) + ")"));
        midiLabel.setText("CC " + mappedCc);
      } else {
        learnBtn.setTooltip(new Tooltip("MIDI Learn (Click, then move a controller)"));
      }

      learnBtn.setOnAction(
          e -> {
            isLearning = !isLearning;
            if (isLearning) {
              learnBtn.setStyle(
                  "-fx-background-color: yellow; -fx-font-size: 10; -fx-padding: 2 4;");
              midiLabel.setText("Learning...");
            } else {
              if (mappedChannel >= 0) {
                learnBtn.setStyle(
                    "-fx-background-color: lightgreen; -fx-font-size: 10; -fx-padding: 2 4;");
                midiLabel.setText("CC " + mappedCc);
              } else {
                learnBtn.setStyle("-fx-font-size: 10; -fx-padding: 2 4;");
                midiLabel.setText("");
              }
            }
          });

      javafx.scene.control.ContextMenu ctxMenu = new javafx.scene.control.ContextMenu();
      javafx.scene.control.MenuItem unmapItem = new javafx.scene.control.MenuItem("Unmap MIDI");
      unmapItem.setOnAction(
          e -> {
            mappedChannel = -1;
            mappedCc = -1;
            isLearning = false;
            learnBtn.setStyle("-fx-font-size: 10; -fx-padding: 2 4;");
            learnBtn.setTooltip(new Tooltip("MIDI Learn"));
            midiLabel.setText("");
            prefs.remove("midi.learn.chan." + key);
            prefs.remove("midi.learn.cc." + key);
          });
      ctxMenu.getItems().add(unmapItem);
      learnBtn.setContextMenu(ctxMenu);

      recBtn = new ToggleButton("●");
      recBtn.setStyle(
          "-fx-font-size: 9; -fx-padding: 2 5; -fx-text-fill: #b71c1c; -fx-font-weight: bold;");
      recBtn.setTooltip(new Tooltip("Record parameter changes into automation curve"));

      playBtn = new ToggleButton("▶");
      playBtn.setStyle(
          "-fx-font-size: 9; -fx-padding: 2 5; -fx-text-fill: #1b5e20; -fx-font-weight: bold;");
      playBtn.setTooltip(new Tooltip("Loop parameter automation"));

      curveBtn = new ToggleButton("📈");
      curveBtn.setStyle("-fx-font-size: 9; -fx-padding: 2 5;");
      curveBtn.setTooltip(new Tooltip("Show/Hide automation curve & LFO editor"));

      double min = 0;
      double max = 1.0;
      if (isInt) {
        max = Math.max(127, initialValue * 2);
      } else {
        String lower = key.toLowerCase();
        if (lower.contains("freq") || lower.contains("pitch") || initialValue > 20.0) {
          max = 20000.0;
        } else if (initialValue > 1.0) {
          max = initialValue * 2.0;
        } else {
          max = 1.0;
        }
      }

      slider = new Slider(min, max, initialValue);
      HBox.setHgrow(slider, Priority.ALWAYS);

      valueLabel = new Label("0");
      valueLabel.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 11;");
      valueLabel.setMinWidth(45);

      automationCanvas = new AutomationCanvas(automationTrack, min, max);
      automationCanvas.setOnCurveModified(
          () -> {
            if (!playBtn.isSelected()) playBtn.setSelected(true);
          });

      TextField minField = new TextField(String.valueOf(min));
      minField.setPrefWidth(55);
      minField.setStyle("-fx-font-size: 9;");
      TextField maxField = new TextField(String.valueOf(max));
      maxField.setPrefWidth(55);
      maxField.setStyle("-fx-font-size: 9;");

      Button setRangeBtn = new Button("Set Range");
      setRangeBtn.setStyle("-fx-font-size: 9; -fx-padding: 2 5;");
      setRangeBtn.setOnAction(
          e -> {
            try {
              double nMin = Double.parseDouble(minField.getText());
              double nMax = Double.parseDouble(maxField.getText());
              slider.setMin(nMin);
              slider.setMax(nMax);
              automationCanvas.setRange(nMin, nMax);
            } catch (NumberFormatException ignored) {
            }
          });

      HBox rangeBox =
          new HBox(4, new Label("Min:"), minField, new Label("Max:"), maxField, setRangeBtn);
      rangeBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
      rangeBox.setPadding(new Insets(2, 0, 2, 0));

      expandBox = new VBox(4, automationCanvas, rangeBox);
      expandBox.setVisible(false);
      expandBox.setManaged(false);

      curveBtn.setOnAction(
          e -> {
            boolean show = curveBtn.isSelected();
            expandBox.setVisible(show);
            expandBox.setManaged(show);
          });

      slider.setOnMousePressed(e -> isDragging = true);
      slider.setOnMouseReleased(e -> isDragging = false);

      slider
          .valueProperty()
          .addListener(
              (obs, oldVal, newVal) -> {
                if (isDragging) {
                  applyValueToVm(newVal.doubleValue(), true);
                }
              });

      HBox header = new HBox(5, nameLabel, learnBtn, recBtn, playBtn, curveBtn, midiLabel);
      header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

      HBox controls = new HBox(5, slider, valueLabel);
      node = new VBox(3, header, controls, expandBox);
      node.setPadding(new Insets(5, 0, 5, 0));
      node.setStyle("-fx-border-color: #ddd; -fx-border-width: 0 0 1 0;");
    }

    public VBox getNode() {
      return node;
    }

    private void applyValueToVm(double val, boolean fromUserOrMidi) {
      if (fromUserOrMidi && recBtn.isSelected() && vm != null) {
        automationTrack.addPoint(vm.getCurrentTime(), val);
        automationCanvas.draw();
      }

      if (isInt) {
        long v = (long) val;
        if (vm != null) vm.setGlobalInt(key, v);
        valueLabel.setText(String.valueOf(v));
      } else {
        if (vm != null) vm.setGlobalFloat(key, val);
        valueLabel.setText(String.format("%.3f", val));
      }
    }

    public void update(double value) {
      if (playBtn.isSelected() && vm != null && !isDragging) {
        double autoVal = automationTrack.evaluate(vm.getCurrentTime(), value);
        slider.setValue(autoVal);
        applyValueToVm(autoVal, false);
      } else if (!isDragging) {
        slider.setValue(value);
        if (isInt) {
          valueLabel.setText(String.valueOf((long) value));
        } else {
          valueLabel.setText(String.format("%.3f", value));
        }
      }
    }

    public void onMidiCc(int val) {
      double pct = val / 127.0;
      double newVal = slider.getMin() + pct * (slider.getMax() - slider.getMin());
      slider.setValue(newVal);
      applyValueToVm(newVal, true);
    }
  }
}
