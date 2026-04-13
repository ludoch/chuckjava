package org.chuck.ide;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.chuck.core.ChuckVM;

/**
 * A control surface for ChucK global variables. Polls the VM for global ints and floats and
 * provides sliders to control them.
 */
public class ControlSurface extends VBox {
  private ChuckVM vm;
  private final Map<String, ControlRow> rows = new HashMap<>();
  private final Timeline timeline;
  private final VBox rowContainer;

  public ControlSurface() {
    setSpacing(10);
    setPadding(new Insets(10));
    setStyle("-fx-background-color: #f8f8f8; -fx-border-color: #ccc; -fx-border-width: 0 1 0 0;");

    Label title = new Label("Control Surface");
    title.setStyle("-fx-font-weight: bold; -fx-text-fill: #333; -fx-font-size: 13;");
    getChildren().add(title);

    rowContainer = new VBox(5);
    getChildren().add(rowContainer);

    timeline = new Timeline(new KeyFrame(Duration.millis(100), e -> update()));
    timeline.setCycleCount(Timeline.INDEFINITE);
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
        ControlRow row = new ControlRow(key, true);
        rows.put(key, row);
        rowContainer.getChildren().add(row.getNode());
      }
      rows.get(key).update(vm.getGlobalInt(key));
    }

    for (String key : floatKeys) {
      if (!rows.containsKey(key)) {
        ControlRow row = new ControlRow(key, false);
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

    public ControlRow(String key, boolean isInt) {
      this.key = key;
      this.isInt = isInt;

      Label nameLabel = new Label(key);
      nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");

      slider = new Slider(0, isInt ? 127 : 1.0, 0);
      HBox.setHgrow(slider, Priority.ALWAYS);

      valueLabel = new Label("0");
      valueLabel.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 11;");
      valueLabel.setMinWidth(45);

      slider.setOnMousePressed(e -> isDragging = true);
      slider.setOnMouseReleased(e -> isDragging = false);

      slider
          .valueProperty()
          .addListener(
              (obs, oldVal, newVal) -> {
                if (isDragging) {
                  if (isInt) {
                    long val = newVal.longValue();
                    vm.setGlobalInt(key, val);
                    valueLabel.setText(String.valueOf(val));
                  } else {
                    double val = newVal.doubleValue();
                    vm.setGlobalFloat(key, val);
                    valueLabel.setText(String.format("%.3f", val));
                  }
                }
              });

      HBox controls = new HBox(5, slider, valueLabel);
      node = new VBox(2, nameLabel, controls);
      node.setPadding(new Insets(5, 0, 5, 0));
      node.setStyle("-fx-border-color: #ddd; -fx-border-width: 0 0 1 0;");
    }

    public VBox getNode() {
      return node;
    }

    public void update(double value) {
      if (!isDragging) {
        slider.setValue(value);
        if (isInt) {
          valueLabel.setText(String.valueOf((long) value));
        } else {
          valueLabel.setText(String.format("%.3f", value));
        }
      }
    }
  }
}
