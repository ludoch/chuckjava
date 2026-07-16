package org.chuck.ide;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

/**
 * Detachable Virtual Console Panel providing search filtering, auto-scroll toggle, timestamp
 * formatting, and pop-out window capabilities matching miniAudicle's standalone Virtual Console.
 */
public class VirtualConsolePanel extends BorderPane {
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
  private final StringBuilder fullLogBuffer = new StringBuilder();
  private final TextArea logArea = new TextArea();
  private final TextField filterField = new TextField();
  private final CheckBox autoScrollBox = new CheckBox("Auto-Scroll");
  private final Button popOutBtn = new Button("↗ Detach Console");
  private final Button clearBtn = new Button("Clear");

  private Pane dockedParent;
  private int dockedIndex = -1;
  private Stage detachedStage = null;
  private boolean isDetached = false;

  public VirtualConsolePanel() {
    logArea.setEditable(false);
    logArea.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12;");
    logArea.setWrapText(true);

    filterField.setPromptText("Filter logs (e.g. Shred [2], error, freq)...");
    filterField.textProperty().addListener((obs, oldV, newV) -> applyFilter());
    HBox.setHgrow(filterField, Priority.ALWAYS);

    autoScrollBox.setSelected(true);

    clearBtn.setOnAction(
        e -> {
          fullLogBuffer.setLength(0);
          logArea.clear();
        });

    popOutBtn.setTooltip(
        new Tooltip("Pop out Virtual Console into a floating window or attach back to footer"));
    popOutBtn.setOnAction(e -> toggleDetach());

    HBox toolbar =
        new HBox(
            8,
            filterField,
            new Separator(Orientation.VERTICAL),
            autoScrollBox,
            clearBtn,
            new Separator(Orientation.VERTICAL),
            popOutBtn);
    toolbar.setPadding(new Insets(4, 6, 4, 6));
    toolbar.setStyle(
        "-fx-background-color: #eaeaea; -fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");

    setTop(toolbar);
    setCenter(logArea);
  }

  public void setDockedParent(Pane parent, int index) {
    this.dockedParent = parent;
    this.dockedIndex = index;
  }

  public void appendLog(String message) {
    Platform.runLater(
        () -> {
          // If the message does not already have a timestamp or bracket and is not a raw newline,
          // prepend timestamp
          String formatted = message;
          if (message != null
              && !message.isEmpty()
              && !message.equals("\n")
              && !message.startsWith("[")) {
            formatted = "[" + LocalTime.now().format(TIME_FMT) + "] " + message;
            if (!formatted.endsWith("\n")) formatted += "\n";
          }

          fullLogBuffer.append(formatted);
          if (fullLogBuffer.length() > 100_000) {
            fullLogBuffer.delete(0, 20_000);
          }

          String filter = filterField.getText();
          if (filter == null || filter.isEmpty()) {
            logArea.appendText(formatted);
            if (logArea.getText().length() > 100_000) {
              logArea.deleteText(0, 20_000);
            }
            if (autoScrollBox.isSelected()) {
              logArea.setScrollTop(Double.MAX_VALUE);
            }
          } else {
            // Check if the new segment matches filter
            if (formatted.toLowerCase().contains(filter.toLowerCase())) {
              logArea.appendText(formatted);
              if (autoScrollBox.isSelected()) {
                logArea.setScrollTop(Double.MAX_VALUE);
              }
            }
          }
        });
  }

  private void applyFilter() {
    String filter = filterField.getText();
    if (filter == null || filter.isEmpty()) {
      logArea.setText(fullLogBuffer.toString());
    } else {
      String filterLower = filter.toLowerCase();
      StringBuilder filtered = new StringBuilder();
      String[] lines = fullLogBuffer.toString().split("\n");
      for (String line : lines) {
        if (line.toLowerCase().contains(filterLower)) {
          filtered.append(line).append("\n");
        }
      }
      logArea.setText(filtered.toString());
    }
    if (autoScrollBox.isSelected()) {
      logArea.setScrollTop(Double.MAX_VALUE);
    }
  }

  private void toggleDetach() {
    if (!isDetached) {
      // Detach from dockedParent
      if (dockedParent != null && dockedParent.getChildren().contains(this)) {
        dockedParent.getChildren().remove(this);
      }
      detachedStage = new Stage();
      detachedStage.setTitle("Virtual Console - ChucK-Java");
      Scene scene = new Scene(this, 750, 450);
      detachedStage.setScene(scene);
      detachedStage.setOnCloseRequest(
          e -> {
            e.consume();
            attachToDock();
          });
      detachedStage.show();
      popOutBtn.setText("↙ Attach Console");
      isDetached = true;
    } else {
      attachToDock();
    }
  }

  public void attachToDock() {
    if (!isDetached) return;
    if (detachedStage != null) {
      detachedStage.close();
      detachedStage = null;
    }
    if (dockedParent != null && !dockedParent.getChildren().contains(this)) {
      if (dockedIndex >= 0 && dockedIndex <= dockedParent.getChildren().size()) {
        dockedParent.getChildren().add(dockedIndex, this);
      } else {
        dockedParent.getChildren().add(this);
      }
      HBox.setHgrow(this, Priority.ALWAYS);
    }
    popOutBtn.setText("↗ Detach Console");
    isDetached = false;
  }
}
